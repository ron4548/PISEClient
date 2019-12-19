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
import java.util.stream.Collectors;
import java.util.stream.Stream;

class InferenceClient {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Alphabet<MessageTypeSymbol> alphabet;
    private List<QueryStats> stats;

    public void setAlphabet(Alphabet<MessageTypeSymbol> alphabet) {
        this.alphabet = alphabet;
    }

    void startConnection(String ip, int port) throws IOException {
        clientSocket = new Socket(ip, port);
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.stats = new ArrayList<>();
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

    Set<MessageTypeSymbol> sendBatchMembershipQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) {
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
        Set<MessageTypeSymbol> discoveredSymbols = new HashSet<>();
        try {
            String output = in.readLine();
            JSONArray queryResultsJson = new JSONArray(output);

            int i = 0;
            for (Query<MessageTypeSymbol, Boolean> query : collection) {
                JSONObject queryResultJson = (JSONObject)queryResultsJson.get(i++);
                boolean result = queryResultJson.getBoolean("answer");
                query.answer(result);

                QueryStats s = QueryStats.fromJson(query, queryResultJson);
                this.stats.add(s);

                if (result) {
                    JSONArray newSymbolsJson = new JSONArray(queryResultJson.getString("probe_result"));
                    List<MessageTypeSymbol> newSymbols = new ArrayList<>(newSymbolsJson.length());
                    for (Object objectJson : newSymbolsJson) {
                        JSONObject symbolJson = (JSONObject) objectJson;
                        newSymbols.add(MessageTypeSymbol.fromJson(symbolJson));
                    }

                    discoveredSymbols.addAll(newSymbols);
                    System.out.println(query);
                    if (discoveredSymbols.size() > 0) {
                        System.out.println("Probing found:");
                        discoveredSymbols.forEach(msg -> System.out.println(msg.getPredicateDescription()));
                    }
                }
            }
            return discoveredSymbols;
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

    public List<QueryStats> getStats() {
        return stats;
    }

    static class QueryStats {
        private final long membershipTime;
        private final long preProbeTime;
        private final long probeTime;
        private final Query<MessageTypeSymbol, Boolean> query;
        private final boolean answer;

        public QueryStats(Query<MessageTypeSymbol, Boolean> query, boolean answer, long membership_time, long pre_probe_time, long probe_time) {
            this.membershipTime = membership_time;
            this.preProbeTime = pre_probe_time;
            this.probeTime = probe_time;
            this.query = query;
            this.answer = answer;
        }

        public static QueryStats fromJson(Query<MessageTypeSymbol, Boolean> query, JSONObject queryResultJson) {
            long membershipTime = queryResultJson.getLong("membership_time");
            long preProbeTime = queryResultJson.has("pre_probe_time") ? queryResultJson.getLong("pre_probe_time") : 0;
            long probeTime = queryResultJson.has("probe_time") ? queryResultJson.getLong("probe_time") : 0;

            return new QueryStats(query, queryResultJson.getBoolean("answer"), membershipTime, preProbeTime, probeTime);
        }

        public long getMembershipTime() {
            return membershipTime;
        }

        public long getPreProbeTime() {
            return preProbeTime;
        }

        public long getProbeTime() {
            return probeTime;
        }

        public Query<MessageTypeSymbol, Boolean> getQuery() {
            return query;
        }

        public boolean getAnswer() {
            return answer;
        }

        @Override
        public String toString() {
            return "QueryStats{" +
                    "membershipTime=" + membershipTime +
                    ", preProbeTime=" + preProbeTime +
                    ", probeTime=" + probeTime +
                    ", query=" + query +
                    ", answer=" + answer +
                    '}';
        }
        
        public String convertToCSV() {
            String[] data = new String[6];
            data[0] = this.query.getInput().toString();
            data[1] = String.valueOf(this.answer);
            data[2] = String.valueOf(this.membershipTime);
            data[3] = String.valueOf(this.preProbeTime);
            data[4] = String.valueOf(this.probeTime);
            data[5] = String.valueOf(this.query.getInput().length());

            return String.join(",", data) + "\n";
        }
    }
}
