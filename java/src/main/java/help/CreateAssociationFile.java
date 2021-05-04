package help;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Creates an association file for ENT-RankLips.
 *
 * @version 2/2/2021
 * @author Shubham Chatterjee
 */

public class CreateAssociationFile {
    private final IndexSearcher paraSearcher;


    public CreateAssociationFile(String indexDir, Set<String> passageRunFiles, String outFile) {
        paraSearcher = LuceneHelper.createSearcher(indexDir, "bm25");

        System.out.println("Creating map...");
        Map<String, Map<String, Set<String>>> entityToPassageMap = createPassagePool(passageRunFiles);

        System.out.println("Writing to file...");
        Set<String> fileStrings = toFileStrings(entityToPassageMap);
        Utilities.writeFile(fileStrings, outFile);
        System.out.println("File written to: " + outFile);
    }

    @NotNull
    private Set<String> toFileStrings(@NotNull Map<String, Map<String, Set<String>>> entityToPassageMap) {
        Set<String> fileStrings = new LinkedHashSet<>();

        for (String query : entityToPassageMap.keySet()) {
            JSONObject queryObject = new JSONObject();
            queryObject.put("query", query);
            queryObject.put("score", 1);
            queryObject.put("rank", 1);
            Map<String, Set<String>> entityMap = entityToPassageMap.get(query);
            for (String entity : entityMap.keySet()) {
                JSONObject docObject = new JSONObject();
                docObject.put("entity", entity);
                Set<String> psgSet = entityMap.get(entity);
                for (String passage : psgSet) {
                    docObject.put("paragraph", passage);
                    queryObject.put("document", docObject);
                    fileStrings.add(queryObject.toJSONString());
                }
            }
        }
        return fileStrings;
    }


    @NotNull
    private Map<String, Map<String, Set<String>>> createPassagePool(@NotNull Set<String> passageRunFiles) {

        Map<String, Map<String, Set<String>>> entityToPassageMap = new HashMap<>();

        for (String run : passageRunFiles) {
            System.out.println("File: " + run);
            getPassages(run, entityToPassageMap);
            System.out.println("[Done].");
        }

        return entityToPassageMap;
    }


    private void getPassages(String run, Map<String, Map<String, Set<String>>> entityToPassageMap)  {
        String line;
        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(run));
            while((line = br.readLine()) != null) {
                String queryId = line.split(" ")[0];
                String paraId = line.split(" ")[2];
                Set<String> psgEntitySet = getParaEntities(paraId);
                createEntityToPassageMap(queryId, paraId, psgEntitySet, entityToPassageMap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(br != null) {
                    br.close();
                } else {
                    System.out.println("Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createEntityToPassageMap(String queryId, String paraId,
                                          @NotNull Set<String> psgEntitySet,
                                          @NotNull Map<String, Map<String, Set<String>>> entityToPassageMap) {

        Map<String, Set<String>> entityMap;
        if (entityToPassageMap.containsKey(queryId)) {
            entityMap = entityToPassageMap.get(queryId);
        } else {
            entityMap = new HashMap<>();
        }

        for (String entity : psgEntitySet) {
            Set<String> psgSet;

            if (entityMap.containsKey(entity)) {
                psgSet = entityMap.get(entity);
            } else {
                psgSet = new HashSet<>();
            }
            psgSet.add(paraId);
            entityMap.put(entity, psgSet);
            entityToPassageMap.put(queryId, entityMap);
        }
    }

    @NotNull
    private Set<String> getParaEntities(String paraId) {
        Set<String> entitySet = new HashSet<>();
        JSONParser parser = new JSONParser();
        try {
            String[] aspectsInPsg = Objects.requireNonNull(LuceneHelper.searchIndex("Id", paraId, paraSearcher)).get("Entities").split("\n");
            for (String aspectStr : aspectsInPsg) {
                if (! aspectStr.isEmpty()) {
                    try {
                        JSONObject jsonObject = (JSONObject) parser.parse(aspectStr);
                        String entityId = jsonObject.get("linkPageId").toString();
                        entitySet.add(entityId);
                    } catch (org.json.simple.parser.ParseException e) {
                        System.out.println("aspectStr: " + aspectStr);
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return entitySet;
    }

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String outFile = args[1];
        Set<String> runFiles = new HashSet<>(Arrays.asList(Arrays.copyOfRange(args, 2, args.length)));
        new CreateAssociationFile(indexDir, runFiles, outFile);
    }
}
