package org.example.learnlib;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.Word;

import java.util.*;
import java.util.stream.Stream;

public class ProbingCache implements MembershipOracle.DFAMembershipOracle<MessageTypeSymbol> {

    private MembershipOracle.DFAMembershipOracle<MessageTypeSymbol> innerOracle;
    private List<InferenceClient.ProbingResult> cache;

    public ProbingCache(DFAMembershipOracle<MessageTypeSymbol> innerOracle) {
        this.innerOracle = innerOracle;
        this.cache = new ArrayList<>();
    }

    @Override
    public void processQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) {
        ArrayList<Query<MessageTypeSymbol, Boolean>> answered = new ArrayList<>();

        for (Query<MessageTypeSymbol, Boolean> query : collection) {
            for (InferenceClient.ProbingResult res : cache) {

                if (res.getPossibleContinuations().stream().anyMatch(word -> word.getSymbol(0).isAny())) {
                    continue;
                }

//                if (res.getDiscoveredSymbols().stream().anyMatch(MessageTypeSymbol::isAny)) {
//                    continue;
//                }
                Word<MessageTypeSymbol> cachedPrefix = res.getQuery().getInput();
//                Set<MessageTypeSymbol> cachedContinuations = res.getDiscoveredSymbols();
//                if (cachedPrefix.isPrefixOf(query.getInput()) && cachedPrefix.length() < query.getInput().length()) {
//                    if (!cachedContinuations.contains(query.getInput().getSymbol(cachedPrefix.length()))){
//                        query.answer(false);
//                        answered.add(query);
////                        System.out.println(query);
////                        System.out.println("Answered by cache - False!");
////                        System.out.println("Prefix: " + cachedPrefix.toString());
//                        break;
//                    }
//                }
                List<Word<MessageTypeSymbol>> cachedContinuations = res.getPossibleContinuations();
                if (cachedPrefix.isPrefixOf(query.getInput()) && cachedPrefix.length() < query.getInput().length()) {
                    if (cachedContinuations.stream().noneMatch(cont -> cont.getSymbol(0).equals(query.getInput().getSymbol(cachedPrefix.length())))){
                        query.answer(false);
                        answered.add(query);
                        System.out.println(query);
                        System.out.println("Answered by cache - False!");
                        System.out.println("Prefix: " + cachedPrefix.toString());
                        break;
                    }
                }
            }
        }

        ArrayList<Query<MessageTypeSymbol, Boolean>> newQueries = new ArrayList<>(collection);
        newQueries.removeAll(answered);
        innerOracle.processQueries(newQueries);
    }

    public void insertToCache(InferenceClient.ProbingResult result) {
        this.cache.add(result);
//        System.out.println("####### Added to cache:");
//        System.out.println(result.getQuery().getInput());
//        System.out.println("Probing options: " + result.getDiscoveredSymbols().toString());
//        System.out.printf("Cache size is %d\n", this.cache.size());
    }

    public DefaultQuery<MessageTypeSymbol, Boolean> findCounterexample(DFA<?, MessageTypeSymbol> hypothesis) {

        for (InferenceClient.ProbingResult result : this.cache) {
            for (Word<MessageTypeSymbol> sym : result.getPossibleContinuations()){
//                if (sym.isAny()) {
//                    continue;
//                }

                System.out.println("Tetsing against " + result.getQuery().getInput().concat(sym));
                if (!hypothesis.accepts(result.getQuery().getInput().concat(sym))) {

                    return new DefaultQuery<>(result.getQuery().getInput().concat(sym), true);
                }
            }
        }

        return null;
    }
}
