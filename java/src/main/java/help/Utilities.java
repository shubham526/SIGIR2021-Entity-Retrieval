package help;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.TermQuery;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class Utilities {
    @NotNull
    public static Map<String, LinkedHashMap<String, Double>> readFile(String inFilePath) {
        BufferedReader br = null;
        String line , queryID ,field2;
        double score;
        Map<String, LinkedHashMap<String, Double>> rankings = new HashMap<>();

        try {
            br = new BufferedReader(new FileReader(inFilePath));
            while((line = br.readLine()) != null) {
                String[] fields = line.split(" ");
                queryID = fields[0];
                field2 = fields[2];
                score = Double.parseDouble(fields[4]);
                LinkedHashMap<String, Double> map = new LinkedHashMap<>();
                if(rankings.containsKey(queryID)) {
                    map = rankings.get(queryID);
                }
                map.put(field2, score);
                rankings.put(queryID, map);
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
        return rankings;
    }

    /**
     * Reads a simple (Key, Value) TSV/CSV file and returns a Map.
     * @param file File to read.
     * @return Map of key, value pairs from file.
     */

    @NotNull
    public static Map<String, String> readTsvOrCsvFile(String file, @NotNull String fileType) {
        BufferedReader br = null;
        Map<String, String> fileMap = new HashMap<>();
        String sep = fileType.equalsIgnoreCase("tsv") ? "\t" : ",";
        String line;

        try {
            br = new BufferedReader(new FileReader(file));
            while((line = br.readLine()) != null) {
                String[] fields = line.split(sep);
                String key  = fields[0];
                String value = fields[1];
                fileMap.put(key, value);
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
        return fileMap;
    }

    /**
     * Write to a file in TSV or CSV format.
     * @param file File to be written.
     * @param fileType TSV or CSV.
     * @param toWrite Data to write.
     */

    public static void writeTsvOrCsvFile(String file, String fileType, @NotNull Map<String, String> toWrite) {
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file,true));

            for(String key : toWrite.keySet() ) {
                String value = toWrite.get(key);
                out.write(key + "\t" + value);
                out.newLine();
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
    @NotNull
    public static Map<String, Map<String, Map<String, Double>>> readSupportPassageRunFile(String runFile) {
        Map<String, Map<String, Map<String, Double>>> supportPassageMap = new LinkedHashMap<>();

        BufferedReader in = null;
        String line;
        Map<String, Map<String, Double>> entityToParaMap;
        Map<String, Double> paraMap;

        try {
            in = new BufferedReader(new FileReader(runFile));
            while ((line = in.readLine()) != null) {
                String[] fields = line.split(" ");
                String queryID = fields[0].split("\\+")[0];
                String entityID = fields[0].split("\\+")[1];
                String paraID = fields[2];
                double paraScore = Double.parseDouble(fields[4]);

                if (supportPassageMap.containsKey(queryID)) {
                    entityToParaMap = supportPassageMap.get(queryID);
                } else {
                    entityToParaMap = new LinkedHashMap<>();
                }
                if (entityToParaMap.containsKey(entityID)) {
                    paraMap = entityToParaMap.get(entityID);
                } else {
                    paraMap = new LinkedHashMap<>();
                }
                paraMap.put(paraID, paraScore);
                entityToParaMap.put(entityID, paraMap);
                supportPassageMap.put(queryID, entityToParaMap);

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
        return supportPassageMap;
    }
    public static void writeFile(@NotNull Set<String> runStrings, String filePath) {
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
    public static void writeFile(@NotNull List<String> runStrings, String filePath) {
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
    @NotNull
    public static <K, V>LinkedHashMap<K, V> sortByValueDescending(@NotNull Map<K, V> map) {
        LinkedHashMap<K, V> reverseSortedMap = new LinkedHashMap<>();
        map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue((Comparator<? super V>) Comparator.reverseOrder()))
                .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));
        return reverseSortedMap;
    }
    /**
     * Reads the stop words file.
     * @param stopWordsFilePath String Path to the stop words file.
     */

    @NotNull
    public static List<String> getStopWords(String stopWordsFilePath) {
        List<String> stopWords = new ArrayList<>();
        BufferedReader br = null;
        String line;

        try {
            br = new BufferedReader(new FileReader(stopWordsFilePath));
            while((line = br.readLine()) != null) {
                stopWords.add(line);
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
        return stopWords;
    }
    public static BooleanQuery toQuery(String queryStr, Analyzer analyzer, String searchField) throws IOException {
        List<String> tokens = new ArrayList<>();

        tokenizeQuery(queryStr, searchField, tokens, analyzer);
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

        for (String token : tokens) {
            booleanQuery.add(new TermQuery(new Term(searchField, token)), BooleanClause.Occur.SHOULD);
        }
        return booleanQuery.build();
    }
    private static void tokenizeQuery(String queryStr, String searchField, @NotNull List<String> tokens, @NotNull Analyzer analyzer) throws IOException {
        TokenStream tokenStream = analyzer.tokenStream(searchField, new StringReader(queryStr));
        tokenStream.reset();
        tokens.clear();
        while (tokenStream.incrementToken() && tokens.size() < 64)
        {
            final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
            tokens.add(token);
        }
        tokenStream.end();
        tokenStream.close();
    }
    public static BooleanQuery toRm3Query(String queryStr,
                                          List<Map.Entry<String, Double>> relevanceModel,
                                          boolean omitQueryTerms,
                                          String searchField,
                                          Analyzer analyzer) throws IOException {
        List<String> tokens = new ArrayList<>();
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

        if (!omitQueryTerms) {
            tokenizeQuery(queryStr, searchField, tokens, analyzer);
            for (String token : tokens) {
                booleanQuery.add(new BoostQuery(new TermQuery(new Term(searchField, token)), 1.0f),
                        BooleanClause.Occur.SHOULD);
            }
        }

        // add RM3 terms
        for (Map.Entry<String, Double> stringFloatEntry : relevanceModel.subList(0, Math.min(relevanceModel.size(), (64 - tokens.size())))) {
            String token = stringFloatEntry.getKey();
            double weight = stringFloatEntry.getValue();
            booleanQuery.add(new BoostQuery(new TermQuery(new Term("Text", token)), (float) weight), BooleanClause.Occur.SHOULD);
        }
        return booleanQuery.build();
    }

    @NotNull
    public static String processQuery(@NotNull String query) {
        return query
                .substring(query.indexOf(":") + 1)          // remove enwiki: from query
                .replaceAll("%20", " ")     // replace %20 with whitespace
                .toLowerCase();                            //  convert query to lowercase

    }


}
