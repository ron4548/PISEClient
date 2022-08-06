package com.pise.client;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ProtocolInferenceMembershipOracle implements MembershipOracle.DFAMembershipOracle<MessageTypeSymbol> {

    private final InferenceClient client;
    private NewSymbolFoundListener listener;

    ProtocolInferenceMembershipOracle(InferenceClient client) {
        this.client = client;
    }

    @Override
    public void processQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) {
        if (collection.size() == 0) return;
        List<InferenceClient.ProbingResult> results = null;
        try {
            results = client.sendBatchMembershipQueries(collection);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
