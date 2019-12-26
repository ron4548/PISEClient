package org.example.learnlib;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.serialization.dot.GraphDOT;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;

public class ProtocolInferenceEQOracle implements EquivalenceOracle.DFAEquivalenceOracle<MessageTypeSymbol> {

    private final ProbingCache probingCache;
    private final EquivalenceOracle.DFAEquivalenceOracle<MessageTypeSymbol> innerOracle;

    public ProtocolInferenceEQOracle(ProbingCache probingCache, DFAEquivalenceOracle<MessageTypeSymbol> innerOracle) {
        this.probingCache = probingCache;
        this.innerOracle = innerOracle;
    }

    @Nullable
    @Override
    public DefaultQuery<MessageTypeSymbol, Boolean> findCounterExample(DFA<?, MessageTypeSymbol> hypothesis, Collection<? extends MessageTypeSymbol> alphabet) {

        DefaultQuery<MessageTypeSymbol, Boolean> cex = this.probingCache.findCounterexample(hypothesis);

        return cex == null ? this.innerOracle.findCounterExample(hypothesis, alphabet) : cex;

    }
}
