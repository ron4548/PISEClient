package org.example.learnlib;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MessageTypeSymbol {
    public static MessageTypeSymbol fromJson(JSONObject symbolJson) {
        String name = symbolJson.getString("name");
        Type type = Type.valueOf(symbolJson.getString("type"));
        Map<Integer, Character> predicate = new HashMap<>();
        JSONObject predicateJson = symbolJson.getJSONObject("predicate");
        for (String key  : predicateJson.keySet()) {
            predicate.put(Integer.valueOf(key), (char)predicateJson.getInt(key));
        }
        return new MessageTypeSymbol(type, name, predicate);
    }

    public enum Type {
        SEND,
        RECEIVE
    }

    private Type type;
    private String name;
    private Map<Integer, Character> predicate;

    MessageTypeSymbol(Type type, String name, Map<Integer, Character> predicate) {
        this.name = name;
        this.predicate = predicate;
        this.type = type;
    }

    String getName() {
        return name;
    }

    Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("[%s]: %s", this.type.toString(), this.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != MessageTypeSymbol.class) {
            return false;
        }

        return this.getName().equals(((MessageTypeSymbol)obj).getName()) && this.type == ((MessageTypeSymbol) obj).type;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    JSONObject asJSON() {
        JSONObject symbolJson = new JSONObject();
        symbolJson.put("name", this.name);
        symbolJson.put("type", this.type.toString());
        JSONObject predicateJson = new JSONObject();
        for (Map.Entry<Integer, Character> constraint : predicate.entrySet()) {
            predicateJson.put(constraint.getKey().toString(), (int)constraint.getValue());
        }
        symbolJson.put("predicate", predicateJson);
        return symbolJson;
    }
}
