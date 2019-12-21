package org.example.learnlib;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;
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
                if (res.getDiscoveredSymbols().stream().anyMatch(MessageTypeSymbol::isAny)) {
                    continue;
                }
                Word<MessageTypeSymbol> cachedPrefix = res.getQuery().getInput();
                Set<MessageTypeSymbol> cachedContinuations = res.getDiscoveredSymbols();
                if (cachedPrefix.isPrefixOf(query.getInput()) && cachedPrefix.length() < query.getInput().length()) {
                    if (!cachedContinuations.contains(query.getInput().getSymbol(cachedPrefix.length()))){
                        query.answer(false);
                        answered.add(query);
                        System.out.println(query);
                        System.out.println("Answered by cache - False!");
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
        System.out.printf("Cache size is %d\n", this.cache.size());
    }
}
