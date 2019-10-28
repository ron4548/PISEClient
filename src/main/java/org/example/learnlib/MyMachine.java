package org.example.learnlib;

import java.util.HashMap;
import java.util.Map;

class MyMachine {

    private MyOutput ok;
    private MyOutput error;
    private boolean seenLogin;

    MyMachine() {
        Map<Integer, Character> predicate = new HashMap<>();
        predicate.put(0, 'o');
        predicate.put(1, 'k');
        this.ok = new MyOutput("ok", predicate);
        this.error = new MyOutput("error", new HashMap<>());
        this.seenLogin = false;
    }

    MyOutput step(MessageTypeSymbol input) {
        if (!seenLogin && input.getName().equals("login")) {
            seenLogin = true;
            return ok;
        }

        if (seenLogin && input.getName().equals("logout")) {
            seenLogin = false;
            return ok;
        }

        return error;
    }
}
