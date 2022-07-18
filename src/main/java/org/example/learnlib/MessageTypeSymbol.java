package org.example.learnlib;

import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MessageTypeSymbol {
    public static MessageTypeSymbol fromJson(JSONObject symbolJson) {
        String name = symbolJson.getString("name");
        Type type = Type.valueOf(symbolJson.getString("type"));
        int id = symbolJson.getInt("id");
        Map<Integer, Character> predicate = new HashMap<>();
        JSONObject predicateJson = symbolJson.getJSONObject("predicate");
        for (String key  : predicateJson.keySet()) {
            predicate.put(Integer.valueOf(key), (char)predicateJson.getInt(key));
        }
        return new MessageTypeSymbol(id, type, name, predicate);
    }

    public boolean isAny() {
        return this.predicate.size() == 0;
    }

    public enum Type {
        SEND,
        RECEIVE
    }

    private Type type;
    private int id;
    private String name;
    private Map<Integer, Character> predicate;

    private int hash;

    MessageTypeSymbol(int id, Type type, String name, Map<Integer, Character> predicate) {
        this.id = id;
        this.name = name;
        this.predicate = Collections.unmodifiableMap(predicate);
        this.type = type;
        this.hash = this.predicate.hashCode();
    }

    String getName() {
        return name;
    }

    Type getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("[%c:%d]", this.getType() == Type.SEND ? 'S' : 'R', this.getId());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != MessageTypeSymbol.class) {
            return false;
        }

        boolean ans = this.predicate.equals(((MessageTypeSymbol) obj).predicate) && this.type == ((MessageTypeSymbol) obj).type;

//        if (ans) {
//            System.out.println("Two symbols found to be the same:");
//            System.out.println(this.getPredicateDescription());
//            System.out.println(((MessageTypeSymbol) obj).getPredicateDescription());
//        }

        return ans;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    JSONObject asJSON() {
        JSONObject symbolJson = new JSONObject();
        symbolJson.put("name", this.name);
        symbolJson.put("type", this.type.toString());
        symbolJson.put("id", this.id);
        JSONObject predicateJson = new JSONObject();
        for (Map.Entry<Integer, Character> constraint : predicate.entrySet()) {
            predicateJson.put(constraint.getKey().toString(), (int)constraint.getValue());
        }
        symbolJson.put("predicate", predicateJson);
        return symbolJson;
    }

    String getPredicateDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%c:%06d]\t{%s} ", this.type == Type.SEND ? 'S' : 'R', this.id, this.name));
        if (this.predicate.size() == 0) {
            return sb.toString();
        }
        int maxIdx = Collections.max(this.predicate.keySet());
        for (int i=0; i<=maxIdx; ++i) {
            if (this.predicate.containsKey(i)) {
                sb.append(String.format("0x%02x ", (int)this.predicate.get(i)));
            } else {
                sb.append("____ ");
            }
        }

        return sb.toString();
    }
}
