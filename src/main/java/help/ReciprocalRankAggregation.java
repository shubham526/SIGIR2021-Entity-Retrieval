package help;

import me.tongfei.progressbar.ProgressBar;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Reciprocal Rank Aggregation of several rankings.
 *
 * @version 1/29/2021
 * @author Shubham Chatterjee
 */

public class ReciprocalRankAggregation {

    public ReciprocalRankAggregation(String runDir, String outFile) {
        Map<String, Map<String, List<String>>> perQueryRankings = readRunFiles(runDir);
        rra(perQueryRankings, outFile);
    }

    private void rra(@NotNull Map<String, Map<String, List<String>>> perQueryRankings, String outFile) {
        ProgressBar pb = new ProgressBar("Progress", perQueryRankings.size());

        for (String queryId : perQueryRankings.keySet()) {
            Set<String> runStrings = doTask(queryId, perQueryRankings.get(queryId));
            Utilities.writeFile(runStrings,  outFile);
            pb.step();
        }
        pb.close();

    }

    @NotNull
    private Set<String> doTask(String queryId, @NotNull Map<String, List<String>> rankings) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();

        for (String file : rankings.keySet()) {
            List<String> list = rankings.get(file);
            for (int i = 0; i < list.size(); i++) {
                String id = list.get(i);
                double score = 1.0 / (i + 1);
                scoreMap.compute(id, (t, oldV) -> (oldV == null) ? score : oldV + score);
            }
        }
        return makeRunStrings(queryId, scoreMap);

    }
    @NotNull
    private Set<String> makeRunStrings(String queryId, Map<String, Double> scoreMap) {
        LinkedHashMap<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);
        Set<String> runStrings = new LinkedHashSet<>();
        int rank = 1;
        String runFileString;
        for (String id : sortedScoreMap.keySet()) {
            String score = String.format("%.2f", sortedScoreMap.get(id));
            runFileString = queryId + " Q0 " + id + " " + rank++ + " " + score + " " + "RRA";
            runStrings.add(runFileString);
        }
        return runStrings;
    }

    @NotNull
    private Map<String, Map<String, List<String>>> readRunFiles(String runDir) {
        Map<String, Map<String, List<String>>> runFiles = new HashMap<>();
        File corpusDir = new File(runDir);
        File[] files = corpusDir.listFiles();
        assert files != null;
        for (File file : files) {
            Map<String, List<String>> runFileMap = readRunFile(file);
            runFiles.put(file.getName(), runFileMap);
        }

        return convertRankings(runFiles);
    }

    @NotNull
    private Map<String, Map<String, List<String>>> convertRankings(@NotNull Map<String, Map<String, List<String>>> runFiles) {
        Set<String> files = runFiles.keySet();
        Map<String, Map<String, List<String>>> rankings = new HashMap<>();

        for (String file : files) {
            Map<String, List<String>> ranking = runFiles.get(file);
            for (String query : ranking.keySet()) {
                Map<String, List<String>> perQuery;
                if (rankings.containsKey(query)) {
                    perQuery = rankings.get(query);
                } else {
                    perQuery = new HashMap<>();
                }
                perQuery.put(file, ranking.get(query));
                rankings.put(query, perQuery);
            }
        }
        return rankings;

    }

    @NotNull
    private Map<String, List<String>> readRunFile(File file) {
        Map<String, List<String>> rankings = new HashMap<>();
        BufferedReader br;
        String line , queryID ,field2;
        try {
            br = new BufferedReader(new FileReader(file));
            while((line = br.readLine()) != null) {
                String[] fields = line.split(" ");
                queryID = fields[0];
                field2 = fields[2];
                List<String> list = new ArrayList<>();
                if(rankings.containsKey(queryID))
                    list = rankings.get(queryID);
                list.add(field2);
                rankings.put(queryID, list);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rankings;

    }

    public static void main(@NotNull String[] args) {
        String runFileDir = args[0];
        String outFile = args[1];
        new ReciprocalRankAggregation(runFileDir, outFile);
    }
}
