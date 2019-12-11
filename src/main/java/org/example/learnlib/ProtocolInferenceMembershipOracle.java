package org.example.learnlib;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ProtocolInferenceMembershipOracle implements MembershipOracle.DFAMembershipOracle<MessageTypeSymbol> {

    private final InferenceClient client;
    private NewSymbolFoundListener listener;

    ProtocolInferenceMembershipOracle(InferenceClient client) {
        this.client = client;
    }

    @Override
    public void processQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) {
        if (collection.size() == 0) return;
        List<InferenceClient.ProbingResult> results = client.sendBatchMembershipQueries(collection);
        if (this.listener != null) {
            listener.onNewSymbols(results);
        }

    }

    void setListener(NewSymbolFoundListener listener) {
        this.listener = listener;
    }

    public interface NewSymbolFoundListener {
        void onNewSymbols(List<InferenceClient.ProbingResult> results);
    }
}
