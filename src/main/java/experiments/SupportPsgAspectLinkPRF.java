package experiments;

import help.LuceneHelper;
import help.Utilities;
import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.util.*;

/**
 * 1. Start with support passage ranking for query-entity pairs.
 * 2. Get set of corresponding entity aspects from support passages. If entity e1 is mentioned in support passages p1,
 * p2, p3, then get the aspect of e1 from each of p1, p2, p3.
 * 3. Rank aspects: Score of aspect = sum of scores of passages it appears in
 * 4. Aspect ranking -> Entity ranking
 *
 * @version 1/30/2021
 * @author Shubham Chatterjee
 */

public class SupportPsgAspectLinkPRF {

    private final IndexSearcher paraSearcher;
    private final Map<String, Map<String, Map<String, Double>>> supportPassageMap;

    public SupportPsgAspectLinkPRF(String paraIndex,
                                   String supportPassageRunFile,
                                   String outFile) {

        this.paraSearcher = LuceneHelper.createSearcher(paraIndex, "bm25");

        System.out.print("Loading support passage file...");
        this.supportPassageMap = Utilities.readSupportPassageRunFile(supportPassageRunFile);
        System.out.println("[Done].");

        doTask(outFile);
    }


    private void doTask(String runFile) {
        Set<String> querySet = supportPassageMap.keySet();
        Map<String, Double> entityScores;
        Set<String> runFileStrings;
        ProgressBar pb = new ProgressBar("Progress",querySet.size() );

        for (String query : querySet) {

            // Re-rank entities for this query
            entityScores =  reRankEntities(supportPassageMap.get(query));

            // Create run file strings and write to run file
            runFileStrings = makeRunFileStrings(query, entityScores);
            Utilities.writeFile(runFileStrings, runFile);

            // Clear for next query
            entityScores.clear();
            runFileStrings.clear();
            pb.step();
        }
        pb.close();
    }

    @NotNull
    private Map<String, Double> reRankEntities(@NotNull Map<String, Map<String, Double>> entityToParaMap) {
        Map<String, Double> entitySores = new HashMap<>();

        for (String entity: entityToParaMap.keySet()) {
            double score = scoreEntity(entity, entityToParaMap.get(entity));
            entitySores.put(entity, score);
        }
        return entitySores;
    }

    private double scoreEntity(String entity, Map<String, Double> psgRanking) {


        // Rank the aspects using the expanded query
        Map<String, Double> aspectScoresForEntity = getAspectsForEntity(entity, psgRanking);

        // Score of entity = Sum of scores of its aspects
        return aspectScoresForEntity.values().stream().mapToDouble(Double::valueOf).sum();
    }

    @NotNull
    private Map<String, Double> getAspectsForEntity(String entity, @NotNull Map<String, Double> psgRanking) {
        Map<String, Double> aspectsForEntity = new HashMap<>();
        JSONParser parser = new JSONParser();

        for (String paraId : psgRanking.keySet()) {
            double paraScore = psgRanking.get(paraId);
            try {
                String[] aspectsInPsg = Objects.requireNonNull(LuceneHelper.searchIndex("Id", paraId, paraSearcher)).get("Entities").split("\n");
                for (String aspectStr : aspectsInPsg) {
                    if (!aspectStr.isEmpty()) {
                        try {
                            JSONObject jsonObject = (JSONObject) parser.parse(aspectStr);
                            String aspectId = jsonObject.get("aspect").toString();
                            String entityId = jsonObject.get("linkPageId").toString();
                            if (entity.equals(entityId)) {
                                aspectsForEntity.compute(aspectId, (t, oldV) -> (oldV == null) ? paraScore : oldV + paraScore);
                                break;
                            }
                        } catch (org.json.simple.parser.ParseException e) {
                            System.out.println("aspectStr=" + aspectStr);
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
        return aspectsForEntity;
    }
    @NotNull
    private Set<String> makeRunFileStrings(String query, @NotNull Map<String, Double> scoreMap)  {
        String runFileString;
        Set<String> runFileStrings = new LinkedHashSet<>();

        int rank = 1;
        String info = "Experiment4";
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

        String outFile = "SupportPsgAspectLinkPRF.run";
        String runFile = runFileDir + "/" + outFile;

        new SupportPsgAspectLinkPRF(paraIndex, passageRanking, runFile);
    }

}
