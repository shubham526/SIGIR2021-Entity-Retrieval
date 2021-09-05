package help;

import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Make a support passage run file using the method Entity Context Neighbour (ECN) of Chatterjee et al., 2019.
 * @author Shubham Chatterjee
 * @version 9/5/2021
 */

public class MakeSupportPsgRun {

    private final Map<String, LinkedHashMap<String, Double>> paraRankings;
    public  Map<String, LinkedHashMap<String, Double>> entityRankings;
    protected final DecimalFormat df;
    private final List<String> runStrings = new ArrayList<>();
    private final IndexSearcher indexSearcher;
    protected int total = 0;
    private final AtomicInteger count = new AtomicInteger(0);
    private final boolean parallel;

    /**
     * Class to represent an Entity Context Document for an entity.
     * @author Shubham Chatterjee
     * @version 05/31/2020
     */
    public static class EntityContextDocument {

        private final List<Document> documentList;
        private final String entity;
        private final List<String> contextEntities;

        /**
         * Constructor.
         * @param documentList List of documents in the pseudo-document
         * @param entity The entity for which the pseudo-document is made
         * @param contextEntities The list of entities in the pseudo-document
         */
        @Contract(pure = true)
        public EntityContextDocument(List<Document> documentList,
                                     String entity,
                                     List<String> contextEntities) {
            this.documentList = documentList;
            this.entity = entity;
            this.contextEntities = contextEntities;
        }

        /**
         * Method to get the list of documents in the ECD.
         * @return String
         */
        public List<Document> getDocumentList() {
            return this.documentList;
        }

        /**
         * Method to get the entity of the ECD.
         * @return String
         */
        public String getEntity() {
            return this.entity;
        }

        /**
         * Method to get the list of context entities in the ECD.
         * @return ArrayList
         */
        public List<String> getEntityList() {
            return this.contextEntities;
        }
    }

    public MakeSupportPsgRun(String paraIndex,
                             String paraRunFile,
                             String entityRunFile,
                             String outFile,
                             boolean parallel) {

        df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);

        this.parallel = parallel;

        System.out.print("Setting up paragraph index...");
        this.indexSearcher = LuceneHelper.createSearcher(paraIndex, "bm25");
        System.out.println("[Done].");

        System.out.print("Loading passage rankings...");
        paraRankings = Utilities.readRunFile(paraRunFile);
        System.out.println("[Done].");

        System.out.print("Loading entity rankings...");
        entityRankings = Utilities.readRunFile(entityRunFile);
        System.out.println("[Done].");

        doTask(outFile);

    }



    private void writeRunFile(@NotNull List<String> runStrings, String filePath) {
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(filePath,true));

            for(String s : runStrings) {
                if (s != null) {
                    out.write(s);
                    out.newLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(out != null) {
                    out.close();
                } else {
                    System.out.println("Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * Method to calculate the feature.
     * Works in parallel using Java 8 parallelStreams.
     * DEFAULT THREAD POOL SIE = NUMBER OF PROCESSORS
     * USE : System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "N") to set the thread pool size
     * @param outputFilePath String Path to the output file.
     */

    private  void doTask(String outputFilePath) {
        Set<String> querySet = entityRankings.keySet();
        total = querySet.size();
        if (parallel) {
            System.out.println("Using Parallel Streams.");
            int parallelism = ForkJoinPool.commonPool().getParallelism();
            int numOfCores = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of available processors = " + numOfCores);
            System.out.println("Number of threads generated = " + parallelism);

            if (parallelism == numOfCores - 1) {
                System.err.println("WARNING: USING ALL AVAILABLE PROCESSORS");
                System.err.println("USE: \"-Djava.util.concurrent.ForkJoinPool.common.parallelism=N\" " +
                        "to set the number of threads used");
            }
            // Do in parallel
            querySet.parallelStream().forEach(this::findSupportPsg);
        } else {
            System.out.println("Using Sequential Streams.");

            // Do in serial
            ProgressBar pb = new ProgressBar("Progress", querySet.size());
            for (String q : querySet) {
                findSupportPsg(q);
                pb.step();
            }
            pb.close();
        }


        // Create the run file
        System.out.print("Writing to run file.....");
        writeRunFile(runStrings, outputFilePath);
        System.out.println("[Done].");
        System.out.println("Run file written at: " + outputFilePath);
    }
    private void findSupportPsg(String queryId) {

        Set<String> retEntitySet = entityRankings.get(queryId).keySet();
        List<String> paraList = new ArrayList<>(paraRankings.get(queryId).keySet());
        for (String entityId : retEntitySet) {
            EntityContextDocument d = createECD(entityId, paraList);
            if (d != null) {
                List<String> contextEntityList = d.getEntityList();
                Map<String, Double> freqDist = getDistribution(contextEntityList, retEntitySet);
                Map<String, Double> scoreMap = scoreDoc(d, freqDist);
                makeRunStrings(queryId, entityId, scoreMap);
            }
        }

        if (parallel) {
            count.getAndIncrement();
            System.out.println("Done query: " + queryId + " ( " + count + "/" + total + " ) ");
        }

    }
    @Nullable
    protected EntityContextDocument createECD(String entityId,
                                              @NotNull List<String> paraList) {
        List<Document> documentList = new ArrayList<>();
        List<String> contextEntityList = new ArrayList<>();
        for (String paraId : paraList) {
            try {
                Document doc = LuceneHelper.searchIndex("Id", paraId, indexSearcher);
                if (doc != null) {
                    // List<String> entityList = Arrays.asList(doc.get("OutlinkIds").split("\n"));
                    List<String> entityList = getEntitiesInPara(doc);

                    if (entityList.isEmpty()) {
                        // If the document does not have any entities then ignore
                        continue;
                    }
                    if (entityList.contains(entityId)) {
                        documentList.add(doc);
                        contextEntityList.addAll(entityList);
                    }
                }

            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }

        }

        // If there are no documents in the pseudo-document
        if (documentList.size() == 0) {
            return null;
        }
        return new EntityContextDocument(documentList, entityId, contextEntityList);
    }

    @NotNull
    protected List<String> getEntitiesInPara(@NotNull Document doc) {
        List<String> entityList = new ArrayList<>();
        String[] paraEntities = doc.get("Entities").split("\n");

        for (String entity : paraEntities) {
            if (! entity.isEmpty()) {
                try {
                    JSONObject jsonObject = new JSONObject(entity);
                    String linkedEntityId = jsonObject.getString("linkPageId");
                    entityList.add(linkedEntityId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }
        return entityList;
    }

    @NotNull
    protected Map<String, Double> getDistribution(@NotNull List<String> contextEntityList,
                                                  Set<String> retEntitySet) {

        HashMap<String, Integer> freqMap = new HashMap<>();

        // For every co-occurring entity do
        for (String entityID : contextEntityList) {
            // If the entity also occurs in the list of entities retrieved for the query then
            if ( retEntitySet.contains(entityID)) {
                freqMap.compute(entityID, (t, oldV) -> (oldV == null) ? 1 : oldV + 1);
            }
        }
        return  toDistribution(freqMap);
    }

    @NotNull
    protected Map<String, Double> toDistribution (@NotNull Map<String, Integer> freqMap) {
        Map<String, Double> dist = new HashMap<>();

        // Calculate the normalizer
        int norm = 0;
        for (int val : freqMap.values()) {
            norm += val;
        }

        // Normalize the map
        for (String word: freqMap.keySet()) {
            int freq = freqMap.get(word);
            double normFreq = (double) freq / norm;
            normFreq = Double.parseDouble(df.format(normFreq));
            if (! (normFreq < 0.0d) ) {
                dist.put(word, normFreq);
            }
        }
        return dist;
    }

    @NotNull
    protected Map<String, Double> scoreDoc(@NotNull EntityContextDocument d, Map<String, Double> freqMap) {
        Map<String, Double> scoreMap = new HashMap<>();

        // Get the list of documents in the pseudo-document corresponding to the entity
        List<Document> documents = d.getDocumentList();

        // For every document do
        for (Document doc : documents) {

            // Get the paragraph id of the document
            String paraId = doc.getField("Id").stringValue();

            // Get the score of the document
            double score = getParaScore(doc, freqMap);

            // Store the paragraph id and score in a HashMap
            scoreMap.put(paraId, score);
        }

        return Utilities.sortByValueDescending(scoreMap);

    }

    /**
     * Method to find the score of a paragraph.
     * This method looks at all the entities in the paragraph and calculates the score from them.
     * For every entity in the paragraph, if the entity has a score from the entity context pseudo-document,
     * then sum over the entity scores and store the score in a HashMap.
     *
     * @param doc  Document
     * @param freqMap HashMap where Key = entity id and Value = score
     * @return Integer
     */

    protected double getParaScore(@NotNull Document doc, Map<String, Double> freqMap) {

        double entityScore, paraScore = 0;
        // Get the entities in the paragraph
        // Make an ArrayList from the String array

        // String[] entityList = doc.get("OutlinkIds").split("\n");
        List<String> entityList = getEntitiesInPara(doc);
        /* For every entity in the paragraph do */
        for (String e : entityList) {
            // Lookup this entity in the HashMap of frequencies for the entities
            // Sum over the scores of the entities to get the score for the passage
            // Store the passage score in the HashMap
            if (freqMap.containsKey(e)) {
                entityScore = freqMap.get(e);
                paraScore += entityScore;
            }

        }
        return paraScore;
    }
    /**
     * Method to make the run file strings.
     *
     * @param queryId  Query ID
     * @param scoreMap HashMap of the scores for each paragraph
     */

    private void makeRunStrings(String queryId, String entityId, Map<String, Double> scoreMap) {
        LinkedHashMap<String, Double> paraScore = Utilities.sortByValueDescending(scoreMap);
        String runFileString;
        int rank = 1;

        for (String paraId : paraScore.keySet()) {
            double score = paraScore.get(paraId);
            if (score > 0) {
                runFileString = queryId + "+" +entityId + " Q0 " + paraId + " " + rank + " " + score + " " + "ECN";
                runStrings.add(runFileString);
                rank++;
            }

        }
    }

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String paraRunFile = args[1];
        String entityRunFile = args[2];
        String outDir = args[3];
        boolean parallel = args[4].equals("true");

        String outFile = outDir + "/ECN-Test.run";
        new MakeSupportPsgRun(indexDir, paraRunFile, entityRunFile, outFile, parallel);

    }

}
