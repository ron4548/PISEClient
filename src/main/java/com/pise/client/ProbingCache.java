package com.pise.client;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.words.Word;

import java.util.*;

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
//                if (res.getDiscoveredSymbols().stream().anyMatch(MessageTypeSymbol::isAny)) {
////                    System.out.println("Skipping result with any:");
////                    System.out.println(res.getQuery());
//                    continue;
//                }
                Word<MessageTypeSymbol> cachedPrefix = res.getQuery().getInput();
                Set<MessageTypeSymbol> cachedContinuations = res.getDiscoveredSymbols();
                if (cachedPrefix.isPrefixOf(query.getInput()) && cachedPrefix.length() < query.getInput().length()) {
                    if (!cachedContinuations.contains(query.getInput().getSymbol(cachedPrefix.length()))){
                        query.answer(false);
                        answered.add(query);
//                        System.out.println(query);
//                        System.out.println("Answered by cache - False!");
//                        System.out.println("Prefix: " + cachedPrefix.toString());
                        break;
                    }
                }
            }
        }

        ArrayList<Query<MessageTypeSymbol, Boolean>> unansweredQueries = new ArrayList<>(collection);
        unansweredQueries.removeAll(answered);
//        ArrayList<Query<MessageTypeSymbol, Boolean>> prefixes = new ArrayList<>(unansweredQueries);
//        ArrayList<Query<MessageTypeSymbol, Boolean>> longestPrefix = new ArrayList<>();
        unansweredQueries.sort(Comparator.comparingInt(q -> q.getInput().length()));
//        for (Query<MessageTypeSymbol, Boolean> query : unansweredQueries) {
//            if (unansweredQueries.stream().anyMatch(otherQuery -> !otherQuery.equals(query) && otherQuery.getInput().isPrefixOf(query.getInput()))) {
//                longestPrefix.add(query);
//            }
//        }
//        prefixes.removeAll(longestPrefix);
        innerOracle.processQueries(unansweredQueries);
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
            for (MessageTypeSymbol sym : result.getDiscoveredSymbols()){
                if (sym.isAny()) {
                    continue;
                }

                if (!hypothesis.accepts(result.getQuery().getInput().append(sym))) {

                    return new DefaultQuery<>(result.getQuery().getInput().append(sym), true);
                }
            }
        }

        return null;
    }
}
