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
 * This is implementation of AspectRetrieval where the aspects from the top-K passages in the candidate set
 * are ranked using query expansion.
 * The candidate set here could be either a support passage candidate set of a BM25 candidate set.
 *
 * 1. Retrieve passages using query.
 * 2. Use top-K passages to derive expansion terms and expand the query.
 * 3. Get the set of aspects from among top-K passages derived for the query.
 * 4. Rank set of aspects obtained in (3).
 * 5. Convert aspect ranking to entity ranking.
 *
 * @author Shubham Chatterjee
 * @version 1/28/2021
 */

public class AspectRetQE {
    private final IndexSearcher paraSearcher;
    private final IndexSearcher catalogSearcher;
    private final Map<String, LinkedHashMap<String, Double>> paraRankings;
    private final Map<String, String> queryIdToNameMap;
    private final boolean omitQueryTerms;
    private final int takeKTerms, takeKDocs;
    private final Analyzer analyzer;
    private final Similarity similarity;


    public AspectRetQE(String paraIndex,
                       String catalogIndex,
                       String passageRanking,
                       String runFile,
                       String stopWordsFile,
                       @NotNull String queryIdToNameMapFile,
                       boolean omitQueryTerms,
                       int takeKTerms,
                       int takeKDocs,
                       String analyzerStr,
                       String similarityStr) {

        this.paraSearcher = LuceneHelper.createSearcher(paraIndex, similarityStr);
        this.catalogSearcher = LuceneHelper.createSearcher(catalogIndex, similarityStr);
        this.omitQueryTerms = omitQueryTerms;
        this.takeKDocs = takeKDocs;
        this.takeKTerms = takeKTerms;
        this.analyzer = LuceneHelper.getAnalyzer(analyzerStr, Arrays.asList("Id", "Name", "Text", "Entities"));
        this.similarity = LuceneHelper.getSimilarity(similarityStr);
        String sep = queryIdToNameMapFile.contains("tsv") ? "tsv" : "csv";

        System.out.print("Loading passage run....");
        this.paraRankings = Utilities.readRunFile(passageRanking);
        System.out.println("[Done].");

        System.out.print("Loading " + sep + " file....");
        this.queryIdToNameMap = Utilities.readTsvOrCsvFile(queryIdToNameMapFile, sep);
        System.out.println("[Done].");

        System.out.print("Loading stop words....");
        List<String> stopWords = Utilities.getStopWords(stopWordsFile);
        System.out.println("[Done].");

        doTask(runFile, stopWords);

    }

    private void doTask(String runFile, List<String> stopWords) {
        Set<String> querySet = paraRankings.keySet();
        List<Document> aspectList = new ArrayList<>();


        Map<String, String> aspectToEntityMap = new HashMap<>();
        Map<String, Double> aspectScores;
        Map<String, Double> entityScores;
        Set<String> runFileStrings;
        ProgressBar pb = new ProgressBar("Progress",querySet.size() );

        for (String query : querySet) {

            String queryStr = queryIdToNameMap.get(query);
            // Get the top-K passages for the query
            // The top-K passages are used for finding the expansion terms
            List<Map.Entry<String, Double>> topKDocs = getTopKDocsForQuery(paraRankings.get(query));

            // Get the set of aspects from the set of passages retrieved for the query
            getAspectListForQuery(topKDocs, aspectToEntityMap);

            // Now use the top-K passages to derive expansion terms for the query and convert it to a BooleanQuery
            BooleanQuery booleanQuery = toBooleanQuery(queryStr, topKDocs, stopWords);

            // Create a list of all aspects in the query in the form of Lucene Documents
            aspectToLuceneDoc(aspectList, aspectToEntityMap.keySet());

            // Now score the aspects using the expanded query
            aspectScores = scoreAspects(booleanQuery, aspectList);

            // Convert the aspect scores to entity scores
            entityScores = aspectToEntityScores(aspectScores, aspectToEntityMap);

            // Create run file strings and write to run file
            runFileStrings = makeRunFileStrings(query, entityScores);
            Utilities.writeFile(runFileStrings, runFile);

            // Clear for next query
            aspectList.clear();
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

    @NotNull
    private Map<String, Double> scoreAspects(BooleanQuery booleanQuery, List<Document> aspectList) {
        Map<String, Double> aspectScores;
        // Build the index of aspects
        // First create the IndexWriter
        IndexWriter iw = RAMIndex.createWriter(analyzer);
        //IndexWriter iw = RAMIndex.createWriter(new EnglishAnalyzer());
        // Now create the index
        try {
            RAMIndex.createIndex(aspectList, iw);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("Number of documents in index = " + RAMIndex.numDocs(iw));
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
        try {
            RAMIndex.close(iw);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return aspectScores;
    }


    private void getAspectListForQuery(@NotNull List<Map.Entry<String, Double>> topKDocs,
                                       Map<String, String> aspectToEntityMap) {

       for (Map.Entry<String, Double> entry : topKDocs) {
           getAspectsInPara(entry.getKey(), aspectToEntityMap);
       }
    }

    private void getAspectsInPara(String paraId,
                                  Map<String, String> aspectToEntityMap) {

        try {
            Document aspectDoc = LuceneHelper.searchIndex("Id", paraId, paraSearcher);
            if (aspectDoc != null) {
                String entity = aspectDoc.get("Entities");
                getAspectToEntityMap(entity, aspectToEntityMap);
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private void aspectToLuceneDoc(List<Document> aspectList, @NotNull Set<String> aspects) {
        for (String aspectId : aspects) {
            try {
                aspectList.add(LuceneHelper.searchIndex("Id", aspectId, catalogSearcher));
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
    }


    private void getAspectToEntityMap(@NotNull String entity, Map<String, String> aspectToEntityMap) {

        String[] aspectList = entity.split("\n");
        for (String aspectStr : aspectList) {
            if (! aspectStr.isEmpty()) {
                try {
                    JSONObject jsonObject = new JSONObject(aspectStr);
                    String aspectId = jsonObject.getString("aspect");
                    String entityId = jsonObject.getString("linkPageId");
                    aspectToEntityMap.put(aspectId, entityId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    @NotNull
    private Set<String> makeRunFileStrings(String query, @NotNull Map<String, Double> scoreMap)  {
        String runFileString;
        Set<String> runFileStrings = new LinkedHashSet<>();

        int rank = 1;
        String info = "AspectRetQE";
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
        String outFileDir = args[3];
        String stopWordsFile = args[4];
        String queryIdToNameMapFile = args[5];
        boolean omitQueryTerms = args[6].equalsIgnoreCase("yes");
        int takeKTerms = Integer.parseInt(args[7]);
        int takeKDocs = Integer.parseInt(args[8]);
        String analyzer = args[9];
        String similarity = args[10];

        System.out.printf("Using top %d  passages for query expansion.\n", takeKDocs);
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

        String outFile = "Experiment1" + "-" + s1 + "-" + s2 + "-" + takeKDocs + "-" + takeKTerms + ".run";
        String runFile = outFileDir + "/" + outFile;

        new AspectRetQE(paraIndex, catalogIndex, passageRanking, runFile, stopWordsFile, queryIdToNameMapFile,
                omitQueryTerms, takeKTerms, takeKDocs, analyzer, similarity);
    }

}

