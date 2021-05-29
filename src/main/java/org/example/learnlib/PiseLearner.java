package org.example.learnlib;

import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFA;
import de.learnlib.algorithms.lstar.dfa.ClassicLStarDFABuilder;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.dfa.DFACacheOracle;
import de.learnlib.filter.statistic.oracle.CounterOracle;
import de.learnlib.oracle.equivalence.*;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.visualization.Visualization;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.SimpleAlphabet;

import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;


public class PiseLearner {

    private final static Logger LOGGER = Logger.getLogger(PiseLearner.class.getName());
    private static final int EXPLORATION_DEPTH = 1;

    public static void main(String[] args) throws IOException {

        LocalDateTime startTime = LocalDateTime.now();
        Alphabet<MessageTypeSymbol> alphabet = new SimpleAlphabet<>();

        InferenceClient client = new InferenceClient();
        client.setAlphabet(alphabet);
        client.startConnection("127.0.0.1", 8080);

        List<InferenceClient.ProbingResult> probingResults = new ArrayList<>();

        ProtocolInferenceMembershipOracle internal = new ProtocolInferenceMembershipOracle(client);
        CounterOracle.DFACounterOracle<MessageTypeSymbol> internalCounter = new CounterOracle.DFACounterOracle<>(internal,"internal");
        ProbingCache probingCache = new ProbingCache(internalCounter);
        DFACacheOracle<MessageTypeSymbol> cacheOracle = DFACacheOracle.createTreePCCacheOracle(alphabet, probingCache);

        CounterOracle.DFACounterOracle<MessageTypeSymbol> mqOracle = new CounterOracle.DFACounterOracle<>(cacheOracle, "cache");
        
        ClassicLStarDFA<MessageTypeSymbol> learner = new ClassicLStarDFABuilder<MessageTypeSymbol>()
                .withAlphabet(alphabet)
                .withOracle(mqOracle)
                .create();

        ProtocolInferenceMembershipOracle.NewSymbolFoundListener listener = probingResults::addAll;
        internal.setListener(listener);

        EquivalenceOracle.DFAEquivalenceOracle<MessageTypeSymbol> eqoracle = new RandomWpMethodEQOracle.DFARandomWpMethodEQOracle<>(mqOracle, 3, 7, 100, 20);
        eqoracle = new IncrementalWMethodEQOracle.DFAIncrementalWMethodEQOracle<>(mqOracle, alphabet, 3);
        eqoracle = new ProtocolInferenceEQOracle(probingCache, eqoracle);

        DefaultQuery<MessageTypeSymbol, Boolean> counterexample = null;
        boolean init = false;
        do {
//            System.out.println("Final observation table:");
//            new ObservationTableASCIIWriter<>().write(learner.getObservationTable(), System.out);
            if (!init) {
                learner.startLearning();
                init = true;
            } else {
                boolean refined = learner.refineHypothesis(counterexample);
                if (!refined) {
                    LOGGER.warning(String.format("No refinement for counterexample: %s", counterexample));
                }
            }
            do {
                List<InferenceClient.ProbingResult> toAdd = new ArrayList<>(probingResults);
                probingResults.clear();
                for (InferenceClient.ProbingResult result : toAdd) {
                    probingCache.insertToCache(result);
                    for (MessageTypeSymbol newSymbol : result.getDiscoveredSymbols()) {
                        if (newSymbol.isAny()) {
                            LOGGER.fine(String.format("Ignoring ANY symbol, ID: %d", newSymbol.getId()));
                            continue;
                        }

                        cacheOracle.addAlphabetSymbol(newSymbol);
                        learner.addAlphabetSymbol(newSymbol);
                    }
                }
            } while(!probingResults.isEmpty());
//            Visualization.visualize(learner.getHypothesisModel(), alphabet);
            LocalDateTime eqStartTime = LocalDateTime.now();

            counterexample = eqoracle.findCounterExample(learner.getHypothesisModel(), alphabet);
            do {
                List<InferenceClient.ProbingResult> toAdd = new ArrayList<>(probingResults);
                probingResults.clear();
                for (InferenceClient.ProbingResult result : toAdd) {
                    probingCache.insertToCache(result);
                    for (MessageTypeSymbol newSymbol : result.getDiscoveredSymbols()) {
                        if (newSymbol.isAny()) {
                            LOGGER.fine(String.format("Ignoring ANY symbol, ID: %d", newSymbol.getId()));
                            continue;
                        }

                        cacheOracle.addAlphabetSymbol(newSymbol);
                        learner.addAlphabetSymbol(newSymbol);
                    }
                }

                if (!toAdd.isEmpty()) {
                    counterexample = eqoracle.findCounterExample(learner.getHypothesisModel(), alphabet);
                }

            } while(!probingResults.isEmpty());

            Duration duration = Duration.between(eqStartTime, LocalDateTime.now());
            LOGGER.info(String.format("Conterexample: %s\nTime to find: %d ms",
                    counterexample, duration.getNano() / 1000000));

        } while (counterexample != null);

//        Experiment.DFAExperiment<MessageTypeSymbol> experiment = new Experiment.DFAExperiment<>(learner, wMethod, alphabet);
//
//        experiment.setProfile(true);
//
//        experiment.setLogModels(true);
//
//        experiment.run();

        client.stopConnection();

        for (MessageTypeSymbol mts : alphabet) {
            System.out.printf("MSG ID %d: %s\n", mts.getId(), mts.getPredicateDescription());
        }

        Duration duration = Duration.between(startTime, LocalDateTime.now());
        LOGGER.info(String.format("Total learning time: %s seconds\nMembership queries: %d\nCache miss rate: %f",
                duration.getSeconds(), mqOracle.getCount(), (float)internalCounter.getCount() / mqOracle.getCount()));

        List<InferenceClient.QueryStats> stats = client.getStats();

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("stats.csv"));
            for (InferenceClient.QueryStats q : stats) {
                writer.write(q.convertToCSV());
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        DFA<?, MessageTypeSymbol> result = learner.getHypothesisModel();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("result_model.dot"));
            GraphDOT.write(result, alphabet, writer, new RemoveNonAcceptingStatesVisualizationHelper<>());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Visualization.visualize(result.transitionGraphView(alphabet), new RemoveNonAcceptingStatesVisualizationHelper<>());

    }
}
