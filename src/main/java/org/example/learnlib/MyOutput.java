package org.example.learnlib;

import java.util.Map;

public class MyOutput {
    private String name;
    private Map<Integer, Character> predicate;

    MyOutput(String name, Map<Integer, Character> predicate) {
        this.name = name;
        this.predicate = predicate;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != MyOutput.class) {
            return false;
        }
        return this.name.equals(((MyOutput) obj).name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }
}
