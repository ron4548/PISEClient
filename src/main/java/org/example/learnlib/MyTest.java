package org.example.learnlib;

import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFABuilder;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.dfa.DFACacheOracle;
import de.learnlib.filter.statistic.oracle.CounterOracle;
import de.learnlib.oracle.equivalence.*;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.incremental.dfa.tree.IncrementalPCDFATreeBuilder;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.SimpleAlphabet;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class MyTest {

    private static final int EXPLORATION_DEPTH = 1;

    public static void main(String[] args) {

        LocalDateTime startTime = LocalDateTime.now();
        Alphabet<MessageTypeSymbol> alphabet = new SimpleAlphabet<>();

        InferenceClient client = new InferenceClient();
        client.setAlphabet(alphabet);
        try {
//            client.startConnection("10.10.43.26", 8080);
            client.startConnection("127.0.0.1", 8080);
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<InferenceClient.ProbingResult> probingResults = new ArrayList<>();

        ProtocolInferenceMembershipOracle internal = new ProtocolInferenceMembershipOracle(client);
        CounterOracle.DFACounterOracle<MessageTypeSymbol> internalCounter = new CounterOracle.DFACounterOracle<>(internal,"internal");
        DFACacheOracle<MessageTypeSymbol> cacheOracle = DFACacheOracle.createTreePCCacheOracle(alphabet, internalCounter);

        IncrementalPCDFATreeBuilder<MessageTypeSymbol> incDfa = null;

        Field f = null; //NoSuchFieldException
        try {
            f = cacheOracle.getClass().getDeclaredField("incDfa");
            f.setAccessible(true);
            incDfa = (IncrementalPCDFATreeBuilder<MessageTypeSymbol>)f.get(cacheOracle);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        CounterOracle.DFACounterOracle<MessageTypeSymbol> mqOracle = new CounterOracle.DFACounterOracle<>(cacheOracle, "cache");
        
        ClassicLStarDFA<MessageTypeSymbol> learner = new ClassicLStarDFABuilder<MessageTypeSymbol>()
                .withAlphabet(alphabet)
                .withOracle(mqOracle)
                .create();

        IncrementalPCDFATreeBuilder<MessageTypeSymbol> finalIncDfa = incDfa;
        ProtocolInferenceMembershipOracle.NewSymbolFoundListener listener = probingResults::addAll;
        internal.setListener(listener);

        EquivalenceOracle.DFAEquivalenceOracle<MessageTypeSymbol> eqoracle = new WpMethodEQOracle.DFAWpMethodEQOracle<>(mqOracle, 2, 20);

        DefaultQuery<MessageTypeSymbol, Boolean> counterexample = null;
        boolean init = false;
        do {
            if (!init) {
                learner.startLearning();
                init = true;
            } else {
                boolean refined = learner.refineHypothesis(counterexample);
                if (!refined) {
                    System.err.println("******** No refinement effected by counterexample!");
                }
            }
            do {
                List<InferenceClient.ProbingResult> toAdd = new ArrayList<>(probingResults);
                probingResults.clear();
                toAdd.forEach(result -> {
                    result.getDiscoveredSymbols().forEach(newSymbol -> {
                        if (newSymbol.isAny()) {
                            System.out.println("Ignoring ANY symbol...");
                            return;
                        }

                        cacheOracle.addAlphabetSymbol(newSymbol);
                        learner.addAlphabetSymbol(newSymbol);
                    });
                });
                toAdd.forEach(result -> {
                    Set<MessageTypeSymbol> discoeveredSymbols = result.getDiscoveredSymbols();
                    Word<MessageTypeSymbol> prefix = result.getQuery().getInput();

                    for (MessageTypeSymbol symbol : alphabet) {
                        if (!discoeveredSymbols.contains(symbol)) {
                            Word<MessageTypeSymbol> newWord = prefix.append(symbol);
                            finalIncDfa.insert(newWord, false);
                        }
                    }
                });
            } while(!probingResults.isEmpty());
//            Visualization.visualize(learner.getHypothesisModel(), alphabet);
            System.out.println("******** Looking for counterexample...");
            counterexample = eqoracle.findCounterExample(learner.getHypothesisModel(), alphabet);
            System.out.printf("******** Conterexample: %s\n", counterexample);

        } while (counterexample != null);

//        Experiment.DFAExperiment<MessageTypeSymbol> experiment = new Experiment.DFAExperiment<>(learner, wMethod, alphabet);
//
//        experiment.setProfile(true);
//
//        experiment.setLogModels(true);
//
//        experiment.run();


        try {
            client.stopConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (MessageTypeSymbol mts : alphabet) {
            System.out.printf("MSG ID %d: %s\n", mts.getId(), mts.getPredicateDescription());
        }

        Duration duration = Duration.between(startTime, LocalDateTime.now());
        System.out.printf("******** It took me %s seconds\n", duration.getSeconds());
        System.out.printf("******** Membership queries: %d\n", mqOracle.getCount());
        System.out.printf("******** Cache miss rate: %f\n", (float)internalCounter.getCount() / mqOracle.getCount());
        DFA<?, MessageTypeSymbol> result = learner.getHypothesisModel();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("result_model.dot"));
            GraphDOT.write(result, alphabet, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Visualization.visualize(learner.getHypothesisModel(), alphabet);
    }
}
