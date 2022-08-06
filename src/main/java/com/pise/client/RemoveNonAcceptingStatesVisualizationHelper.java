package com.pise.client;

import net.automatalib.util.graphs.traversal.BaseDFSVisitor;
import net.automatalib.visualization.VisualizationHelper;

import java.util.Map;

public class RemoveNonAcceptingStatesVisualizationHelper<N, E> implements VisualizationHelper<N, E> {

    private Map<N, Boolean> isSinkState;

    @Override
    public boolean getNodeProperties(N n, Map<String, String> map) {
        return map.containsKey("accepting") && map.get("accepting").equals("true");
    }

    @Override
    public boolean getEdgeProperties(N n, E e, N n1, Map<String, String> map) {
        return true;
    }
}

