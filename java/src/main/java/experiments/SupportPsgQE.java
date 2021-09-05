package experiments;

import help.LuceneHelper;
import help.RAMIndex;
import help.Utilities;
import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;


/**
 * 1. Start with support passage ranking for query-entity pairs.
 * 2. Get set of corresponding entity aspects from support passages. If entity e1 is mentioned in support passages p1,
 * p2, p3, then get the aspect of e1 from each of p1, p2, p3.
 * 3. Expand query with terms from support passages.
 * 4. Rank aspects using expanded query.
 * 5. Aspect ranking -> Entity ranking
 *
 * @version 1/30/2021
 * @author Shubham Chatterjee
 */

public class SupportPsgQE {

    private final IndexSearcher paraSearcher;
    private final IndexSearcher catalogSearcher;
    private final Map<String, Map<String, Map<String, Double>>> supportPassageMap;
    private final Map<String, String> queryIdToNameMap;
    private final boolean omitQueryTerms;
    private final int takeKTerms;
    private final Analyzer analyzer;
    private final Similarity similarity;


    public SupportPsgQE(String paraIndex,
                        String catalogIndex,
                        String supportPassageRunFile,
                        String outFile,
                        String stopWordsFile,
                        @NotNull String queryIdToNameMapFile,
                        boolean omitQueryTerms,
                        int takeKTerms,
                        String analyzerStr,
                        String similarityStr) {

        this.paraSearcher = LuceneHelper.createSearcher(paraIndex, similarityStr);
        this.catalogSearcher = LuceneHelper.createSearcher(catalogIndex, similarityStr);
        this.omitQueryTerms = omitQueryTerms;
        this.takeKTerms = takeKTerms;
        this.analyzer = LuceneHelper.getAnalyzer(analyzerStr, Arrays.asList("Id", "Name", "Text", "Entities"));
        this.similarity = LuceneHelper.getSimilarity(similarityStr);
        String sep = queryIdToNameMapFile.contains("tsv") ? "tsv" : "csv";



        System.out.print("Loading support passage run....");
        this.supportPassageMap = Utilities.readSupportPassageRunFile(supportPassageRunFile);
        System.out.println("[Done].");

        System.out.print("Loading " + sep + " file....");
        this.queryIdToNameMap = Utilities.readTsvOrCsvFile(queryIdToNameMapFile, sep);
        System.out.println("[Done].");

        System.out.print("Loading stop words....");
        List<String> stopWords = Utilities.getStopWords(stopWordsFile);
        System.out.println("[Done].");

        doTask(outFile, stopWords);
    }


    private void doTask(String runFile, List<String> stopWords) {
        Set<String> querySet = supportPassageMap.keySet();
        Map<String, Double> entityScores;
        Set<String> runFileStrings;
        ProgressBar pb = new ProgressBar("Progress",querySet.size());

        for (String query : querySet) {
            String queryStr = queryIdToNameMap.get(query);
            
            // Re-rank entities for this query
            entityScores =  reRankEntities(queryStr, supportPassageMap.get(query), stopWords);

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
    private Map<String, Double> reRankEntities(String query,
                                               @NotNull Map<String, Map<String, Double>> entityToParaMap,
                                               List<String> stopWords) {
        Map<String, Double> entitySores = new HashMap<>();
        
        for (String entity: entityToParaMap.keySet()) {
            double score = scoreEntity(query, entity, entityToParaMap.get(entity), stopWords);
            entitySores.put(entity, score);
        }
        return entitySores;
    }

    private double scoreEntity(String query, String entity, Map<String, Double> psgRanking,
                               List<String> stopWords) {

        // Get the aspects of the entity
        Set<String> aspectsForEntity = getAspectsForEntity(entity, psgRanking);

        // Convert the query to a BooleanQuery
        // Expansion terms derived from support passage ranking for the query and entity
        BooleanQuery booleanQuery = toBooleanQuery(query, new ArrayList<>(psgRanking.entrySet()), stopWords);

        // Rank the aspects using the expanded query
        Map<String, Double> aspectScoresForEntity = scoreAspects(booleanQuery, aspectToLuceneDoc(aspectsForEntity));

        // Score of entity = Sum of scores of its aspects
        return aspectScoresForEntity.values().stream().mapToDouble(Double::valueOf).sum();
    }

    @NotNull
    private Set<String> getAspectsForEntity(String entity, @NotNull Map<String, Double> psgRanking) {
        Set<String> aspectsForEntity = new HashSet<>();
        
        for (String paraId : psgRanking.keySet()) {
            try {
                String[] aspectsInPsg = Objects.requireNonNull(LuceneHelper.searchIndex("Id", paraId, paraSearcher)).get("Entities").split("\n");
                for (String aspectStr : aspectsInPsg) {
                    if (! aspectStr.isEmpty()) {
                        try {
                            JSONObject jsonObject = new JSONObject(aspectStr);
                            String aspectId = jsonObject.getString("aspect");
                            String entityId = jsonObject.getString("linkPageId");
                            if (entity.equals(entityId)) {
                                aspectsForEntity.add(aspectId);
                            }
                        } catch (JSONException e) {
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

    private BooleanQuery toBooleanQuery(String query, List<Map.Entry<String, Double>> topKDocs,
                                        List<String> stopWords) {

        Map<String, Double> termDist = getTermDistribution(topKDocs, stopWords);
        // Convert the query to an expanded BooleanQuery
        BooleanQuery booleanQuery = null;
        List<Map.Entry<String, Double>> allWordFreqList = new ArrayList<>(termDist.entrySet());
        List<Map.Entry<String, Double>> expansionTerms = allWordFreqList.subList(0,
                Math.min(takeKTerms, allWordFreqList.size()));
        try {
            booleanQuery = Utilities.toRm3Query(query, expansionTerms, omitQueryTerms, "Text", analyzer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return booleanQuery;



    }

    @NotNull
    private Map<String, Double> getTermDistribution(@NotNull List<Map.Entry<String, Double>> topKDocs,
                                                    List<String> stopWords) {
        Map<String, Double> freqDist = new HashMap<>();

        // compute score normalizer
        float normalizer = 0.0f;
        for (Map.Entry<String, Double> entry : topKDocs) {
            normalizer += entry.getValue();
        }

        for (Map.Entry<String, Double> entry : topKDocs) {
            double weight = entry.getValue() / normalizer;
            String processedDocText = getProcessedDocText(entry.getKey(), stopWords);
            try {
                addTokens(processedDocText, weight, freqDist);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return Utilities.sortByValueDescending(freqDist);
    }
    private  void addTokens(String content,
                            double weight,
                            Map<String,Double> wordFreq) throws IOException {

        TokenStream tokenStream = analyzer.tokenStream("Text", new StringReader(content));
        tokenStream.reset();
        while (tokenStream.incrementToken()) {
            final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
            wordFreq.compute(token, (t, oldV) -> (oldV == null) ? weight : oldV + weight);
        }
        tokenStream.end();
        tokenStream.close();
    }

    /**
     * Helper method.
     * Takes a ParaID and returns the list of words in the paragraph after preprocessing.
     * @param pid String ParaID
     * @return List of words in the paragraph.
     */

    @NotNull
    private String getProcessedDocText(String pid, List<String> stopWords) {
        // Get the document corresponding to the paragraph from the lucene index
        String docContents = "";
        try {
            Document doc = LuceneHelper.searchIndex("Id", pid, paraSearcher);
            assert doc != null;
            docContents = doc.get("Text");
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        List<String> words = preProcess(docContents, stopWords);
        return String.join(" ", words);
    }
    /**
     * Pre-process the text.
     * (1) Lowercase words.
     * (2) Remove all spaces.
     * (3) Remove special characters.
     * (4) Remove stop words.
     * @param text String Text to pre-process
     * @return List of words from the text after pre-processing.
     */

    @NotNull
    private List<String> preProcess(String text, @NotNull List<String> stopWords) {

        // Convert all words to lowercase
        text = text.toLowerCase();

        // Remove all spaces
        text = text.replace("\n", " ").replace("\r", " ");

        // Remove all special characters such as - + ^ . : , ( )
        text = text.replaceAll("[\\-+.^*:,;=(){}\\[\\]\"]","");

        // Get all words
        List<String> words = new ArrayList<>(Arrays.asList(text.split(" ")));
        words.removeAll(Collections.singleton(null));
        words.removeAll(Collections.singleton(""));

        // Remove all stop words
        words.removeIf(stopWords::contains);

        return words;
    }

    @NotNull
    private Map<String, Double> scoreAspects(BooleanQuery booleanQuery, List<Document> aspectList) {
        Map<String, Double> aspectScores;
        // Build the index of aspects
        // First create the IndexWriter
        IndexWriter iw = RAMIndex.createWriter(analyzer);

        // Now create the index
        try {
            RAMIndex.createIndex(aspectList, iw);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create the IndexSearcher

        IndexSearcher is = null;
        try {
            is = RAMIndex.createSearcher(similarity, iw);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Now search the query
        assert is != null;
        aspectScores = Utilities.sortByValueDescending(RAMIndex.searchIndex(booleanQuery, 1000, is));
        return aspectScores;
    }

    @NotNull
    private List<Document> aspectToLuceneDoc(@NotNull Set<String> aspects) {
        List<Document> aspectList = new ArrayList<>();
        for (String aspectId : aspects) {
            try {
                aspectList.add(LuceneHelper.searchIndex("Id", aspectId, catalogSearcher));
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
        return aspectList;
    }



    @NotNull
    private Set<String> makeRunFileStrings(String query, @NotNull Map<String, Double> scoreMap)  {
        String runFileString;
        Set<String> runFileStrings = new LinkedHashSet<>();

        int rank = 1;
        String info = "SupportPsgQE";
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (String entity : sortedScoreMap.keySet()) {
            runFileString = query + " " + "0" + " " + entity + " " +
                    rank++ + " " + sortedScoreMap.get(entity) + " "+ info ;
            runFileStrings.add(runFileString);
        }
        return runFileStrings;
    }
    public static void main(@NotNull String[] args) {

        String s1 = null, s2;

        String paraIndex = args[0];
        String catalogIndex = args[1];
        String passageRanking = args[2];
        String runFileDir = args[3];
        String stopWordsFile = args[4];
        String queryIdToNameMapFile = args[5];
        boolean omitQueryTerms = args[6].equalsIgnoreCase("yes");
        int takeKTerms = Integer.parseInt(args[7]);
        String analyzer = args[8];
        String similarity = args[9];

        System.out.printf("Using %d terms for query expansion.\n", takeKTerms);

        if (omitQueryTerms) {
            System.out.println("Using RM1");
            s2 = "rm1";
        } else {
            System.out.println("Using RM3");
            s2 = "rm3";
        }


        switch (analyzer) {
            case "std" :
                System.out.println("Analyzer: Standard");
                break;
            case "eng":
                System.out.println("Analyzer: English");
                break;
            default:
                System.out.println("Wrong choice of analyzer! Exiting.");
                System.exit(1);
        }
        switch (similarity) {
            case "BM25" :
            case "bm25":
                System.out.println("Similarity: BM25");
                s1 = "bm25";
                break;
            case "LMJM":
            case "lmjm":
                System.out.println("Similarity: LMJM");
                s1 = "lmjm";
                break;
            case "LMDS":
            case "lmds":
                System.out.println("Similarity: LMDS");
                s1 = "lmds";
                break;

            default:
                System.out.println("Wrong choice of similarity! Exiting.");
                System.exit(1);
        }

        String outFile = "Experiment3" + "-" + s1 + "-" + s2 + "-" + takeKTerms + ".run";
        String runFile = runFileDir + "/" + outFile;

        new SupportPsgQE(paraIndex, catalogIndex, passageRanking, runFile, stopWordsFile, queryIdToNameMapFile, omitQueryTerms,
                takeKTerms, analyzer, similarity);
    }


}

