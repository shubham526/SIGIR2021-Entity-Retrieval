package experiments;

import help.LuceneHelper;
import help.Utilities;
import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.util.*;

/**
 * Use query to retrieve aspects from aspect index directly.
 * Convert aspect scores to entity scores.
 * Score of entity = Sum of aspect scores of aspects it links to.
 *
 * @author Shubham Chatterjee
 * @version 2/24/2021
 */

public class CatalogRetrieval {

    private final IndexSearcher aspectSearcher;
    private final Map<String, String> aspectToEntityMap;
    private final int topK;


    public CatalogRetrieval(String catalogIndex,
                            String queryIdToNameMapFile,
                            String runFile,
                            @NotNull String aspectToEntityMapFile,
                            int n) {
        this.aspectSearcher = LuceneHelper.createSearcher(catalogIndex, "bm25");
        this.topK = n;
        String sep = aspectToEntityMapFile.contains("tsv") ? "tsv" : "csv";

        System.out.print("Loading aspect to entity map file....");
        this.aspectToEntityMap = Utilities.readTsvOrCsvFile(aspectToEntityMapFile, sep);
        System.out.println("[Done].");

        System.out.print("Loading queryId to Name map file....");
        Map<String, String> queryIdToNameMap = Utilities.readTsvOrCsvFile(queryIdToNameMapFile, sep);
        System.out.println("[Done].");

        doTask(queryIdToNameMap, runFile);

        System.out.println("Run file written to:" + runFile);
    }


    private void doTask(@NotNull Map<String, String> queryMap, String runFile) {
        ProgressBar pb = new ProgressBar("Progress",queryMap.size() );
        for (String queryId : queryMap.keySet()) {
            try {
                String queryStr = queryMap.get(queryId);
                BooleanQuery booleanQuery = Utilities.toQuery(queryStr, new EnglishAnalyzer(), "Text");
                TopDocs topDocs  = LuceneHelper.searchIndex(booleanQuery, topK, aspectSearcher);
                Map<String, Double> docScores = toDocScores(topDocs);
                Map<String, Double> entityScores = aspectToEntityScores(docScores);
                Set<String> runFileStrings = makeRunFileStrings(queryId, entityScores);
                Utilities.writeFile(runFileStrings, runFile);
                pb.step();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        pb.close();
    }

    @NotNull
    private Map<String, Double> aspectToEntityScores(@NotNull Map<String, Double> aspectScores) {
        Map<String, Double> entityScores = new HashMap<>();
        for (String aspectId : aspectScores.keySet()) {
            if (aspectToEntityMap.containsKey(aspectId)) {
                String entity = aspectToEntityMap.get(aspectId);
                double score = aspectScores.get(aspectId);
                entityScores.compute(entity, (t, oldV) -> (oldV == null) ? score : oldV + score);
            }
        }
        return entityScores;
    }

    @NotNull
    private Map<String, Double> toDocScores(@NotNull TopDocs topDocs) {
        Map<String, Double> docScores = new HashMap<>();
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        for (int i = 0; i < scoreDocs.length; i++) {
            try {
                Document doc = aspectSearcher.doc(scoreDocs[i].doc);
                double score = topDocs.scoreDocs[i].score;
                String id = doc.get("Id");
                docScores.put(id, score);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return docScores;
    }

    @NotNull
    private Set<String> makeRunFileStrings(String query, @NotNull Map<String, Double> scoreMap)  {
        String runFileString;
        Set<String> runFileStrings = new LinkedHashSet<>();

        int rank = 1;
        String info = "Baseline-CatalogRetrieval-Top-" + topK;
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (String entity : sortedScoreMap.keySet()) {
            runFileString = query + " " + "Q0" + " " + entity + " " +
                    rank++ + " " + sortedScoreMap.get(entity) + " "+ info ;
            runFileStrings.add(runFileString);
        }
        return runFileStrings;
    }

    public static void main(@NotNull String[] args) {
        String catalogIndex = args[0];
        String queryIdToNameMapFile = args[1];
        String saveDir = args[2];
        String aspectToEntityMapFile = args[3];
        int n = Integer.parseInt(args[4]);

        String runFile = saveDir + "/Baseline-CatalogRetrieval.run";

        new CatalogRetrieval(catalogIndex, queryIdToNameMapFile, runFile, aspectToEntityMapFile, n);
    }
}

