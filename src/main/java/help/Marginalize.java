package help;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Convert a support passage ranking to a passage ranking by marginalizing over the entities.
 * The queries are matched against those in the candidate passage ranking.
 * If some query is not found in the support passage ranking, then it is copied over from the candidate ranking.
 *
 * @version 1/30/2021
 * @author Shubham Chatterjee
 */

public class Marginalize {
    public Marginalize(String supportPassageRunFile,
                       String candidatePassageRunFile,
                       String newPassageRunFile) {



        Map<String, LinkedHashMap<String, Double>> generatedRunFileMap = new HashMap<>();
        Map<String, Map<String, Double>> newRunFileMap = new HashMap<>();
        Set<String> runStrings = new LinkedHashSet<>();

        System.out.print("Reading support passage run file...");
        Map<String, Map<String, Map<String, Double>>> supportPassageRunFileMap = readSupportPassageRunFile(supportPassageRunFile);
        System.out.println("[Done].");

        System.out.print("Reading candidate passage run file....");
        Map<String, LinkedHashMap<String, Double>> candidatePassageRunFileMap = Utilities.readFile(candidatePassageRunFile);
        System.out.println("[Done].");


        System.out.print("Marginalizing over entities in support passage run file....");
        marginalize(supportPassageRunFileMap, generatedRunFileMap);
        System.out.println("[Done].");

        System.out.println("Making new run file....");
        makeNewRunFile(generatedRunFileMap, candidatePassageRunFileMap, newRunFileMap);
        makeRunFileStrings(newRunFileMap, runStrings);
        System.out.println("[Done].");

        System.out.print("Writing new passage run to file...");
        Utilities.writeFile(runStrings, newPassageRunFile);
        System.out.println("[Done].");

        System.out.println("New run file written to: " + newPassageRunFile);



    }
    @NotNull
    private Map<String, Map<String, Map<String, Double>>> readSupportPassageRunFile(String runFile) {

        Map<String, Map<String, Map<String, Double>>> queryMap = new HashMap<>();

        BufferedReader in = null;
        String line;
        Map<String, Map<String, Double>> paraMap;
        Map<String, Double> entityMap;

        try {
            in = new BufferedReader(new FileReader(runFile));
            while ((line = in.readLine()) != null) {
                String[] fields = line.split(" ");
                String queryID = fields[0].split("\\+")[0];
                String entityID = fields[0].split("\\+")[1];
                String paraID = fields[2];
                double paraScore = Double.parseDouble(fields[4]);

                if (queryMap.containsKey(queryID)) {
                    paraMap = queryMap.get(queryID);
                } else {
                    paraMap = new HashMap<>();
                    //System.out.println(queryID);
                }
                if (paraMap.containsKey(paraID)) {
                    entityMap = paraMap.get(paraID);
                } else {
                    entityMap = new HashMap<>();
                }
                entityMap.put(entityID, paraScore);
                paraMap.put(paraID, entityMap);
                queryMap.put(queryID, paraMap);

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                } else {
                    System.out.println("Input Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return queryMap;
    }

    private void marginalize(@NotNull Map<String, Map<String, Map<String, Double>>> supportPassageRunFileMap,
                             Map<String, LinkedHashMap<String, Double>> generatedRunFileMap) {

        Set<String> querySet = supportPassageRunFileMap.keySet();
        for (String queryID : querySet) {
            Map<String, Map<String, Double>> paraMap = supportPassageRunFileMap.get(queryID);
            LinkedHashMap<String, Double> innerMap = new LinkedHashMap<>();
            Set<String> paraSet = paraMap.keySet();
            for (String paraID : paraSet) {
                Map<String, Double> entityMap = paraMap.get(paraID);
                double score = sum(entityMap);
                innerMap.put(paraID, score);
            }
            generatedRunFileMap.put(queryID, innerMap);
        }
    }
    private void makeNewRunFile(Map<String, LinkedHashMap<String, Double>> generatedRunFileMap,
                                @NotNull Map<String, LinkedHashMap<String, Double>> runFileMap,
                                Map<String, Map<String, Double>> newRunFileMap) {
        Set<String> querySet = runFileMap.keySet();

        for (String queryID : querySet) {
            if (generatedRunFileMap.containsKey(queryID)) {
                newRunFileMap.put(queryID, generatedRunFileMap.get(queryID));
            } else {
                System.out.println("Did not find query: " + queryID);
                System.out.println("Populating the query with the passages found in the candidate passage run file");
                newRunFileMap.put(queryID, runFileMap.get(queryID));
            }
        }

    }

    /**
     * Make run file strings
     * @param newRunFileMap Map
     * @param runFileStrings List
     */
    private void makeRunFileStrings(@NotNull Map<String, Map<String, Double>> newRunFileMap,
                                    Set<String> runFileStrings) {
        String runString;
        for (String queryID : newRunFileMap.keySet()) {
            int rank = 1;
            Map<String, Double> paraMap = newRunFileMap.get(queryID);
            Map<String, Double> sortedMap = Utilities.sortByValueDescending(paraMap);
            for (String paraID : sortedMap.keySet()) {
                double score = sortedMap.get(paraID);
                runString = queryID + " Q0 " + paraID + " " + rank++ + " " + score + " " +  "Marginalize";
                runFileStrings.add(runString);
            }
        }
    }

    /**
     * Sum over elements in a Map.
     * Helpful for marginalization.
     * @param map Map
     * @return double
     */
    private double sum(@NotNull Map<String, Double> map) {
        return map.values().stream().mapToDouble(Double::valueOf).sum();
    }

    public static void main(@NotNull String[] args) {
        String supportPassageRunFile = args[0];
        String candidatePassageRunFile = args[1];
        String newPassageRunFile = args[2];
        new Marginalize(supportPassageRunFile, candidatePassageRunFile, newPassageRunFile);
    }

}
