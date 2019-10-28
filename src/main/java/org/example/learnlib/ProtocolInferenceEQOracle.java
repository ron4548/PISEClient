package org.example.learnlib;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.serialization.dot.GraphDOT;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;

public class ProtocolInferenceEQOracle implements EquivalenceOracle.DFAEquivalenceOracle<MessageTypeSymbol> {

    private final InferenceClient client;

    ProtocolInferenceEQOracle(InferenceClient client) {
        this.client = client;
    }

    @Nullable
    @Override
    public DefaultQuery<MessageTypeSymbol, Boolean> findCounterExample(DFA<?, MessageTypeSymbol> hypothesis, Collection<? extends MessageTypeSymbol> alphabet) {
        try {
            GraphDOT.write(hypothesis, alphabet, System.out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
