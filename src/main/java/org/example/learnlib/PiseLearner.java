package org.example.learnlib;

import com.sun.org.apache.xpath.internal.operations.Bool;
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
import de.learnlib.datastructure.observationtable.OTUtils;

import java.io.*;
import java.lang.reflect.Array;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;


public class PiseLearner {

    private final static Logger LOGGER = Logger.getLogger(PiseLearner.class.getName());
    private static final int EXPLORATION_DEPTH = 1;
    private final static String OUT_DIR = "./out/";

    public static void main(String[] args) throws IOException {
        int conjecture = 0;

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

        EquivalenceOracle.DFAEquivalenceOracle<MessageTypeSymbol> eqoracle = new RandomWpMethodEQOracle.DFARandomWpMethodEQOracle<>(mqOracle, 2, 20, 100000, 20);
    //    eqoracle = new IncrementalWMethodEQOracle.DFAIncrementalWMethodEQOracle<>(mqOracle, alphabet, 3);
        eqoracle = new ProtocolInferenceEQOracle(probingCache, eqoracle);

        ArrayList<DefaultQuery<MessageTypeSymbol, Boolean>> counterexamples = new ArrayList<>();
        boolean init = false;
        do {
//            System.out.println("Final observation table:");
//            new ObservationTableASCIIWriter<>().write(learner.getObservationTable(), System.out);
            if (!init) {
                learner.startLearning();
                init = true;
            } else {
                for  (DefaultQuery<MessageTypeSymbol, Boolean> cex : counterexamples) {
                    boolean refined = learner.refineHypothesis(cex);
                    outputGraph(learner, alphabet, "snapshot.dot");
                    outputAlphabet(alphabet, "snapshot_alphabet.txt");
                    if (!refined) {
                        LOGGER.warning(String.format("No refinement for counterexample: %s", cex));
                    }
                }

                counterexamples.clear();
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

                        if (!alphabet.contains(newSymbol)) {
                            LOGGER.info(String.format("New alphabet symbol: %s", newSymbol.getPredicateDescription()));
                            cacheOracle.addAlphabetSymbol(newSymbol);
                            learner.addAlphabetSymbol(newSymbol);
                            outputGraph(learner, alphabet, "snapshot.dot");
                            outputAlphabet(alphabet, "snapshot_alphabet.txt");
                        }

                        boolean refined = learner.refineHypothesis(new DefaultQuery<>(result.getQuery().getInput().append(newSymbol), true));
                        if (refined) {
                            LOGGER.info(String.format("Refinement occured for: %s", result.getQuery().getInput().append(newSymbol)));
                            outputGraph(learner, alphabet, "snapshot.dot");
                            outputAlphabet(alphabet, "snapshot_alphabet.txt");
                        }
                    }
                }
            } while(!probingResults.isEmpty());

            LocalDateTime eqStartTime = LocalDateTime.now();
            do {
                LOGGER.info(String.format("Conjecture: %d. Looking for counterexample", conjecture));
//                Visualization.visualize(learner.getHypothesisModel().transitionGraphView(alphabet), new RemoveNonAcceptingStatesVisualizationHelper<>());

                outputGraph(learner, alphabet, String.format("conjecture_%d.dot", conjecture));
                outputAlphabet(alphabet,  String.format("conjecture_%d_alphabet.txt", conjecture++));

                DefaultQuery<MessageTypeSymbol, Boolean> cex = eqoracle.findCounterExample(learner.getHypothesisModel(), alphabet);
                if (cex != null) {
                    counterexamples.add(cex);
                }
                List<InferenceClient.ProbingResult> toAdd = new ArrayList<>(probingResults);
                probingResults.clear();
                for (InferenceClient.ProbingResult result : toAdd) {
                    probingCache.insertToCache(result);
                    for (MessageTypeSymbol newSymbol : result.getDiscoveredSymbols()) {
                        if (newSymbol.isAny()) {
                            LOGGER.fine(String.format("Ignoring ANY symbol, ID: %d", newSymbol.getId()));
                            continue;
                        }

                        if (!learner.getHypothesisModel().accepts(result.getQuery().getInput().append(newSymbol))) {
                            counterexamples.add(new DefaultQuery<>(result.getQuery().getInput().append(newSymbol), true));
                        }

                        if (alphabet.contains(newSymbol)) {
//                            LOGGER.info("alphabet already exist");
                            continue;
                        }

                        LOGGER.info(String.format("New alphabet symbol: %s", newSymbol.getPredicateDescription()));

                        cacheOracle.addAlphabetSymbol(newSymbol);
                        learner.addAlphabetSymbol(newSymbol);
                        outputGraph(learner, alphabet, "snapshot.dot");
                        outputAlphabet(alphabet, "snapshot_alphabet.txt");
                    }
                }

            } while(!probingResults.isEmpty());

            Duration duration = Duration.between(eqStartTime, LocalDateTime.now());
            LOGGER.info(String.format("Counterexamples: Time to find: %d ms", duration.getNano() / 1000000));
            outputCex(counterexamples, String.format("cex_%d.txt", conjecture));
            for (DefaultQuery<MessageTypeSymbol, Boolean> cex : counterexamples) {
                LOGGER.info(String.format("Counterexample: %s", cex));
            }

        } while (!counterexamples.isEmpty());

//        Experiment.DFAExperiment<MessageTypeSymbol> experiment = new Experiment.DFAExperiment<>(learner, wMethod, alphabet);
//
//        experiment.setProfile(true);
//
//        experiment.setLogModels(true);
//
//        experiment.run();

        client.stopConnection();

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
        outputGraph(learner, alphabet, "final_graph.dot");
        outputAlphabet(alphabet, "final_alphabet.txt");


        Visualization.visualize(result.transitionGraphView(alphabet), new RemoveNonAcceptingStatesVisualizationHelper<>());

    }

    private static void outputCex(List<DefaultQuery<MessageTypeSymbol, Boolean>> counterexamples, String filename) {
        try {
            FileWriter writer = new FileWriter(OUT_DIR + filename);
            for (DefaultQuery<MessageTypeSymbol, Boolean> cex : counterexamples) {
                writer.write(cex.toString() + "\n");
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void outputGraph(ClassicLStarDFA<MessageTypeSymbol> learner, Alphabet<MessageTypeSymbol> alphabet, String filename) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(OUT_DIR + filename));
            GraphDOT.write(learner.getHypothesisModel(), alphabet, writer, new RemoveNonAcceptingStatesVisualizationHelper<>());
            writer.close();
            Runtime.getRuntime().exec(new String[] {"dot", "-O", "-Tpng", OUT_DIR + filename } );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void outputAlphabet(Alphabet<MessageTypeSymbol> alphabet, String filename) {
        try {
            FileWriter writer = new FileWriter(OUT_DIR + filename);
            for (MessageTypeSymbol mts : alphabet) {
                writer.write(String.format("MSG ID %06d:\t%s\n", mts.getId(), mts.getPredicateDescription()));
            }
            writer.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
