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
 * Baseline method for entity ranking.
 * Method (ECM): Retrieve passages for query. Get all entities from all passages for the query.
 *               Score of entity = Sum of scores of passages it appears in.
 * @author Shubham Chatterjee
 * @version 2/6/2021
 */

public class CatalogRetrieval {

    private final IndexSearcher paraSearcher;
    Set<String> runFileStrings = new LinkedHashSet<>();
    Map<String, LinkedHashMap<String, Double>> paraRankings;

    public CatalogRetrieval(String indexDir, String passageRun, String runFile) {

        System.out.print("Loading passage rankings...");
        this.paraRankings = Utilities.readFile(passageRun);
        System.out.println("[Done].");
        this.paraSearcher = LuceneHelper.createSearcher(indexDir, "bm25");

        doTask(paraRankings, runFile);
    }

    private void doTask(@NotNull Map<String, LinkedHashMap<String, Double>> paraRankings, String runFile) {
        Set<String> querySet = paraRankings.keySet();

        ProgressBar pb = new ProgressBar("Progress",querySet.size() );
        for (String queryId : querySet) {
            Map<String, Double> entityRanking = rankPassageEntities(paraRankings.get(queryId));
            Set<String> runFileStrings = makeRunFileStrings(queryId, entityRanking);
            Utilities.writeFile(runFileStrings, runFile);
            pb.step();
        }
        pb.close();
    }

    private void doTask(String queryId) {
        runFileStrings.addAll(makeRunFileStrings(queryId, rankPassageEntities(paraRankings.get(queryId))));
        System.out.println("Done:" + queryId);
    }

    @NotNull
    private Map<String, Double> rankPassageEntities(@NotNull LinkedHashMap<String, Double> psgRanking) {
        Map<String, Double> entityRanking = new HashMap<>();

        for (String paraId : psgRanking.keySet()) {
            double score = psgRanking.get(paraId);
            //Set<String> psgEntities = getPassageEntities(paraId);
            List<String> psgEntities = getPassageEntities(paraId);
            for (String entity : psgEntities) {
                entityRanking.compute(entity, (t, oldV) -> (oldV == null) ? score : oldV + score);
            }
        }
        return entityRanking;
    }

    @NotNull
    private List<String> getPassageEntities(String paraId) {

        List<String> psgEntities = new ArrayList<>();
        JSONParser parser = new JSONParser();
        try {
            String[] entities = Objects.requireNonNull(LuceneHelper.searchIndex("Id", paraId, paraSearcher)).get("Entities").split("\n");
            for (String entity : entities) {
                if (! entity.isEmpty()) {
                    try {
                        JSONObject jsonObject = (JSONObject) parser.parse(entity);
                        String entityId = jsonObject.get("linkPageId").toString();
                       psgEntities.add(entityId);
                    } catch (org.json.simple.parser.ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return psgEntities;
    }

    @NotNull
    private Set<String> makeRunFileStrings(String query, @NotNull Map<String, Double> scoreMap)  {
        String runFileString;
        Set<String> runFileStrings = new LinkedHashSet<>();

        int rank = 1;
        String info = "Baseline-ECM";
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (String entity : sortedScoreMap.keySet()) {
            runFileString = query + " " + "0" + " " + entity + " " +
                    rank++ + " " + sortedScoreMap.get(entity) + " "+ info ;
            runFileStrings.add(runFileString);
        }
        return runFileStrings;
    }

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String passageRun = args[1];
        String runFile = args[2];

        new CatalogRetrieval(indexDir, passageRun, runFile);
    }
}
