package org.example.learnlib;

import de.learnlib.api.query.Query;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

class InferenceClient {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Alphabet<MessageTypeSymbol> alphabet;

    public void setAlphabet(Alphabet<MessageTypeSymbol> alphabet) {
        this.alphabet = alphabet;
    }

    void startConnection(String ip, int port) throws IOException {
        clientSocket = new Socket(ip, port);
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    @Deprecated
    List<MessageTypeSymbol> probe(Word<MessageTypeSymbol> prefix, Alphabet<MessageTypeSymbol> knownSymbols) {
        JSONObject queryJson = new JSONObject();
        queryJson.put("type", "probe");
        queryJson.put("prefix", symbolsArrayToJson(prefix.asList()));
        queryJson.put("alphabet", symbolsArrayToJson(knownSymbols));

        out.println(queryJson.toString());

        out.println("DONE");

        try {
            String output = in.readLine();
            JSONArray newSymbolsJson = new JSONArray(output);
//            System.out.println(newSymbolsJson.toString());
            List<MessageTypeSymbol> newSymbols = new ArrayList<>(newSymbolsJson.length());
            for (Object objectJson : newSymbolsJson) {
                JSONObject symbolJson = (JSONObject)objectJson;
                newSymbols.add(MessageTypeSymbol.fromJson(symbolJson));
            }
            System.out.println("Probe query found:");
            newSymbols.forEach(msg -> System.out.println(msg.getPredicateDescription()));
            return newSymbols;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    @Deprecated
    boolean sendMembershipQuery(Query<MessageTypeSymbol, Boolean> query) {
        JSONObject queryJson = new JSONObject();
        queryJson.put("type", "membership");
        JSONArray querySymbolsJson = new JSONArray();
        for (MessageTypeSymbol symbol : query.getInput()) {
            querySymbolsJson.put(symbol.asJSON());
        }
        queryJson.put("input", querySymbolsJson);
        out.println(queryJson.toString());
        System.out.println(query.getInput().toString());
        out.println("DONE");

        try {
            String output = in.readLine();
            System.out.println(output);

            if (output.equals("True")) {
                query.answer(true);
                return true;
            } else {
                query.answer(false);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private JSONArray symbolsArrayToJson(Collection<MessageTypeSymbol> symbols) {
        JSONArray symbolsArrayJson = new JSONArray();
        for (MessageTypeSymbol symbol : symbols) {
            symbolsArrayJson.put(symbol.asJSON());
        }

        return symbolsArrayJson;
    }

    private JSONObject queryToJson(Query<MessageTypeSymbol, Boolean> query) {
        JSONObject queryJson = new JSONObject();
        queryJson.put("input", symbolsArrayToJson(query.getInput().asList()));
        return queryJson;
    }

    List<ProbingResult> sendBatchMembershipQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) {
        JSONObject batchJson = new JSONObject();
        batchJson.put("type", "membership_batch");

        // Put current alphabet, for probing
        batchJson.put("alphabet", symbolsArrayToJson(this.alphabet));

        // Put all the queries
        JSONArray queriesArrayJson = new JSONArray();
        for (Query<MessageTypeSymbol, Boolean> query : collection) {
            queriesArrayJson.put(queryToJson(query));
        }
        batchJson.put("queries", queriesArrayJson);
        out.println(batchJson.toString());
        out.println("DONE");
        List<ProbingResult> results = new ArrayList<>();
        try {
            String output = in.readLine();
            JSONArray queryResultsJson = new JSONArray(output);

            int i = 0;
            for (Query<MessageTypeSymbol, Boolean> query : collection) {
                JSONObject queryResultJson = (JSONObject)queryResultsJson.get(i++);
                boolean result = queryResultJson.getBoolean("answer");
                query.answer(result);

                if (result) {
                    JSONArray newSymbolsJson = new JSONArray(queryResultJson.getString("probe_result"));
                    Set<MessageTypeSymbol> newSymbols = new HashSet<>(newSymbolsJson.length());
                    for (Object objectJson : newSymbolsJson) {
                        JSONObject symbolJson = (JSONObject) objectJson;
                        newSymbols.add(MessageTypeSymbol.fromJson(symbolJson));
                    }

                    results.add(new ProbingResult(query, newSymbols));
                    System.out.println(query);
                    if (newSymbols.size() > 0) {
                        System.out.println("Probing found:");
                        newSymbols.forEach(msg -> System.out.println(msg.getPredicateDescription()));
                    }
                }
            }
            return results;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    void stopConnection() throws IOException {
        out.println("BYE");
        in.close();
        out.close();
        clientSocket.close();
    }

    public static class ProbingResult {
        private Query<MessageTypeSymbol, Boolean> query;
        private Set<MessageTypeSymbol> discoveredSymbols = new HashSet<>();

        public ProbingResult(Query<MessageTypeSymbol, Boolean> query, Set<MessageTypeSymbol> discoveredSymbols) {
            this.query = query;
            this.discoveredSymbols = discoveredSymbols;
        }

        public Query<MessageTypeSymbol, Boolean> getQuery() {
            return query;
        }

        public Set<MessageTypeSymbol> getDiscoveredSymbols() {
            return discoveredSymbols;
        }
    }
}
