package experiments;

import help.LuceneHelper;
import help.Utilities;
import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 *
 * This is a combination of AspectRetrieval and AspectLinkPRF where aspects retrieved with the query are filtered using
 * the candidate set.
 * The candidate set here could be either a support passage candidate set of a BM25 candidate set.
 *
 * 1. Retrieve passages using query.
 * 2. Get the set of aspects from among top-K passages derived for the query.
 * 3. Rank aspects: Score of aspect = Sum of scores of passages it occurs in.
 * 4. Convert aspect ranking to entity ranking.
 *
 * @author Shubham Chatterjee
 * @version 1/30/2021
 */

public class AspectRetAspectLinkPRF {
    private final IndexSearcher paraSearcher;
    private final Map<String, LinkedHashMap<String, Double>> paraRankings;
    private final int takeKDocs;

    public AspectRetAspectLinkPRF(String paraIndex,
                                  String passageRanking,
                                  String runFile,
                                  int takeKDocs) {

        this.paraSearcher = LuceneHelper.createSearcher(paraIndex, "bm25");
        this.paraRankings = Utilities.readRunFile(passageRanking);
        this.takeKDocs = takeKDocs;
        doTask(runFile);
    }

    private void doTask(String runFile) {
        Set<String> querySet = paraRankings.keySet();

        Map<String, String> aspectToEntityMap = new HashMap<>();
        Map<String, Double> aspectScores = new HashMap<>();
        Map<String, Double> entityScores;
        Set<String> runFileStrings;
        ProgressBar pb = new ProgressBar("Progress",querySet.size() );

        for (String query : querySet) {

            // Get the top-K passages for the query
            // The top-K passages are used for finding the expansion terms
            List<Map.Entry<String, Double>> topKDocs = getTopKDocsForQuery(paraRankings.get(query));

            // Get the set of aspects from the set of passages retrieved for the query
            getAspectListForQuery(topKDocs, aspectToEntityMap, aspectScores);

            // Convert the aspect scores to entity scores
            entityScores = aspectToEntityScores(aspectScores, aspectToEntityMap);

            // Create run file strings and write to run file
            runFileStrings = makeRunFileStrings(query, entityScores);
            Utilities.writeFile(runFileStrings, runFile);

            // Clear for next query
            aspectToEntityMap.clear();
            aspectScores.clear();
            entityScores.clear();
            runFileStrings.clear();
            pb.step();
        }
        pb.close();
    }

    @NotNull
    private List<Map.Entry<String, Double>> getTopKDocsForQuery(@NotNull LinkedHashMap<String, Double> psgRankings) {
        List<Map.Entry<String, Double>> allPsgRankings = new ArrayList<>(psgRankings.entrySet());

        return allPsgRankings.subList(0,
                Math.min(takeKDocs, allPsgRankings.size()));

    }

    @NotNull
    private Map<String, Double> aspectToEntityScores(@NotNull Map<String, Double> aspectScores,
                                                     Map<String, String> aspectToEntityMap) {
        Map<String, Double> entityScores = new HashMap<>();

        for (String aspectId : aspectScores.keySet()) {
            String entity =  aspectToEntityMap.get(aspectId);
            double score = aspectScores.get(aspectId);
            entityScores.compute(entity, (t, oldV) -> (oldV == null) ? score : oldV + score);
        }
        return entityScores;
    }

    /**
     * Get the aspects from the top-K passages retrieved for the query.
     * This method populates a Map of (AspectId, EntityId).
     * It also scores the aspects.
     * Score of aspect = Sum of scores of passages it appears in.
     * @param topKDocs List of top-K passages for the query.
     * @param aspectToEntityMap Map of (AspectId, EntityId).
     * @param aspectScores Map of (AspectId, AspectScore).
     */


    private void getAspectListForQuery(@NotNull List<Map.Entry<String, Double>> topKDocs,
                                       Map<String, String> aspectToEntityMap,
                                       Map<String, Double> aspectScores) {

        for (Map.Entry<String, Double> entry : topKDocs) {
            String paraId = entry.getKey();
            double paraScore = entry.getValue();
            try {
                Document aspectDoc = LuceneHelper.searchIndex("Id", paraId, paraSearcher);
                if (aspectDoc != null) {
                    String[] aspectList = aspectDoc.get("Entities").split("\n");
                    for (String aspectStr : aspectList) {
                        if (!aspectStr.isEmpty()) {
                            try {
                                JSONObject jsonObject = new JSONObject(aspectStr);
                                String aspectId = jsonObject.getString("aspect");
                                String entityId = jsonObject.getString("linkPageId");
                                aspectToEntityMap.put(aspectId, entityId);
                                aspectScores.compute(aspectId, (t, oldV) -> (oldV == null) ? paraScore : oldV + paraScore);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    @NotNull
    private Set<String> makeRunFileStrings(String query, @NotNull Map<String, Double> scoreMap)  {
        String runFileString;
        Set<String> runFileStrings = new LinkedHashSet<>();

        int rank = 1;
        String info = "AspectRetAspectLinkPRF";
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (String entity : sortedScoreMap.keySet()) {
            runFileString = query + " " + "0" + " " + entity + " " +
                    rank++ + " " + sortedScoreMap.get(entity) + " "+ info ;
            runFileStrings.add(runFileString);
        }
        return runFileStrings;
    }

    public static void main(@NotNull String[] args) {

        String paraIndex = args[0];
        String passageRanking = args[1];
        String runFileDir = args[2];
        int takeKDocs = Integer.parseInt(args[3]);

        System.out.printf("Using top %d  passages for query expansion.\n", takeKDocs);

        String outFile = "Experiment5-" + takeKDocs +  ".run";
        String runFile = runFileDir + "/" + outFile;

        new AspectRetAspectLinkPRF(paraIndex, passageRanking, runFile, takeKDocs);
    }

}

