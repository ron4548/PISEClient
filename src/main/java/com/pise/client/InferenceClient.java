package com.pise.client;

import de.learnlib.api.query.Query;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

class InferenceClient {
    private final static Logger LOGGER = Logger.getLogger(InferenceClient.class.getName());
    private Socket clientSocket;
    private DataOutputStream out;
    private DataInputStream in;
    private Alphabet<MessageTypeSymbol> alphabet;
    private List<QueryStats> stats;

    public void setAlphabet(Alphabet<MessageTypeSymbol> alphabet) {
        this.alphabet = alphabet;
    }

    void startConnection(String ip, int port) throws IOException {
        clientSocket = new Socket(ip, port);
        out = new DataOutputStream((clientSocket.getOutputStream()));
        in = new DataInputStream(clientSocket.getInputStream());
        this.stats = new ArrayList<>();
    }

    private void sendJson(JSONObject json) throws IOException {
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length);
        out.flush();
        out.write(data);
        out.flush();
    }

    private JSONObject readJson() throws IOException {
        int length = in.readInt();
        if (length > 0) {
            byte[] data = new byte[length];
            in.readFully(data, 0, data.length);
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        }

        return null;
    }

    @Deprecated
    List<MessageTypeSymbol> probe(Word<MessageTypeSymbol> prefix, Alphabet<MessageTypeSymbol> knownSymbols) throws IOException {
        JSONObject queryJson = new JSONObject();
        queryJson.put("type", "probe");
        queryJson.put("prefix", symbolsArrayToJson(prefix.asList()));
        queryJson.put("alphabet", symbolsArrayToJson(knownSymbols));

        this.sendJson(queryJson);

        try {
            String output = in.readLine();
            JSONArray newSymbolsJson = new JSONArray(output);
//            System.out.println(newSymbolsJson.toString());
            List<MessageTypeSymbol> newSymbols = new ArrayList<>(newSymbolsJson.length());
            for (Object objectJson : newSymbolsJson) {
                JSONObject symbolJson = (JSONObject)objectJson;
                newSymbols.add(MessageTypeSymbol.fromJson(symbolJson));
            }
            StringWriter writer = new StringWriter();
            writer.write("Probe query found:\n");
            newSymbols.forEach(msg -> writer.write(msg.getPredicateDescription() + '\n'));
            LOGGER.fine(writer.toString());
            return newSymbols;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    @Deprecated
    boolean sendMembershipQuery(Query<MessageTypeSymbol, Boolean> query) throws IOException {
        JSONObject queryJson = new JSONObject();
        queryJson.put("type", "membership");
        JSONArray querySymbolsJson = new JSONArray();
        for (MessageTypeSymbol symbol : query.getInput()) {
            querySymbolsJson.put(symbol.asJSON());
        }
        queryJson.put("input", querySymbolsJson);
        this.sendJson(queryJson);
        System.out.println(query.getInput().toString());

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

    List<ProbingResult> sendBatchMembershipQueries(Collection<? extends Query<MessageTypeSymbol, Boolean>> collection) throws IOException {
        LOGGER.info(String.format("Sending batch of %d queries...", collection.size()));

        JSONObject batchJson = new JSONObject();
        batchJson.put("type", "membership_batch");

        // Put current alphabet, for probing
//        batchJson.put("alphabet", symbolsArrayToJson(this.alphabet));

        // Put all the queries
        JSONArray queriesArrayJson = new JSONArray();
        for (Query<MessageTypeSymbol, Boolean> query : collection) {
            queriesArrayJson.put(queryToJson(query));
            LOGGER.info(query.toString());
        }
        batchJson.put("queries", queriesArrayJson);
        this.sendJson(batchJson);
        List<ProbingResult> results = new ArrayList<>();
        try {
            JSONArray queryResultsJson = this.readJson().getJSONArray("result");

            int i = 0;
            for (Query<MessageTypeSymbol, Boolean> query : collection) {
                JSONObject queryResultJson = (JSONObject)queryResultsJson.get(i++);
                boolean result = queryResultJson.getBoolean("answer");
                query.answer(result);

                QueryStats s = QueryStats.fromJson(query, queryResultJson);
                this.stats.add(s);

                if (result) {
                    JSONArray newSymbolsJson = queryResultJson.getJSONArray("probe_result");
                    Set<MessageTypeSymbol> newSymbols = new HashSet<>(newSymbolsJson.length());
                    for (Object objectJson : newSymbolsJson) {
                        JSONObject symbolJson = (JSONObject) objectJson;
                        newSymbols.add(MessageTypeSymbol.fromJson(symbolJson));
                    }

                    results.add(new ProbingResult(query, newSymbols));
                    StringWriter writer = new StringWriter();
                    writer.write(query.toString() + '\n');
                    if (newSymbols.size() > 0) {
                        writer.write("Probing found:\n");
                        newSymbols.forEach(msg -> writer.write(msg.getPredicateDescription() + '\n'));
                    } else {
                        writer.write("No symbols probed\n");
                    }
                    LOGGER.info(writer.toString());
                }
            }
            return results;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    void stopConnection() throws IOException {
        out.writeInt(0);
        out.flush();
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
//            long membershipTime = queryResultJson.getLong("membership_time");
            long preProbeTime = queryResultJson.has("pre_probe_time") ? queryResultJson.getLong("pre_probe_time") : 0;
            long probeTime = queryResultJson.has("probe_time") ? queryResultJson.getLong("probe_time") : 0;

            return new QueryStats(query, queryResultJson.getBoolean("answer"), 0, preProbeTime, probeTime);
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
