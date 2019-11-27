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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class InferenceClient {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    void startConnection(String ip, int port) throws IOException {
        clientSocket = new Socket(ip, port);
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    List<MessageTypeSymbol> probe(Word<MessageTypeSymbol> prefix, Alphabet<MessageTypeSymbol> knownSymbols) {
        JSONObject queryJson = new JSONObject();
        queryJson.put("type", "probe");
        JSONArray prefixSymbolsJson = new JSONArray();
        for (MessageTypeSymbol symbol : prefix) {
            prefixSymbolsJson.put(symbol.asJSON());
        }
        queryJson.put("prefix", prefixSymbolsJson);

        JSONArray alphabetJson = new JSONArray();
        for (MessageTypeSymbol symbol : knownSymbols) {
            alphabetJson.put(symbol.asJSON());
        }
        queryJson.put("alphabet", alphabetJson);

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

    private JSONObject queryToJson(Query<MessageTypeSymbol, Boolean> query) {
        JSONObject queryJson = new JSONObject();
        JSONArray querySymbolsJson = new JSONArray();
        for (MessageTypeSymbol symbol : query.getInput()) {
            querySymbolsJson.put(symbol.asJSON());
        }
        queryJson.put("input", querySymbolsJson);
        return queryJson;
    }

    void sendBatchMembershipQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) {
        JSONObject batchJson = new JSONObject();
        batchJson.put("type", "membership_batch");

        JSONArray queriesArrayJson = new JSONArray();
        for (Query<MessageTypeSymbol, Boolean> query : collection) {
            queriesArrayJson.put(queryToJson(query));
        }
        batchJson.put("inputs", queriesArrayJson);
        out.println(batchJson.toString());
        out.println("DONE");

        try {

            for (Query<MessageTypeSymbol, Boolean> query : collection) {
                String output = in.readLine();
                System.out.println(output);

                if (output.equals("True")) {
                    query.answer(true);
                } else {
                    query.answer(false);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    void stopConnection() throws IOException {
        out.println("BYE");
        in.close();
        out.close();
        clientSocket.close();
    }
}
