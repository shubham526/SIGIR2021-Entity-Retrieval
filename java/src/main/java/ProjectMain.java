import experiments.*;
import help.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ProjectMain {
    public static void main(@NotNull String[] args) {
        String command = args[0];

        if (command.equalsIgnoreCase("aspect-ret-aspect-link-prf")) {
            String paraIndex = args[1];
            String passageRanking = args[2];
            String runFileDir = args[3];
            int takeKDocs = Integer.parseInt(args[4]);

            System.out.printf("Using top %d  passages for query expansion.\n", takeKDocs);

            String outFile = "AspectRetAspectLinkPRF-" + takeKDocs +  ".run";
            String runFile = runFileDir + "/" + outFile;

            new AspectRetAspectLinkPRF(paraIndex, passageRanking, runFile, takeKDocs);
        } else if (command.equalsIgnoreCase("aspect-ret-qe")) {
            String s1 = null, s2;

            String paraIndex = args[1];
            String catalogIndex = args[2];
            String passageRanking = args[3];
            String outFileDir = args[4];
            String stopWordsFile = args[5];
            String queryIdToNameMapFile = args[6];
            boolean omitQueryTerms = args[7].equalsIgnoreCase("yes");
            int takeKTerms = Integer.parseInt(args[8]);
            int takeKDocs = Integer.parseInt(args[9]);
            String analyzer = args[10];
            String similarity = args[11];

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

            String outFile = "AspectRetQE" + "-" + s1 + "-" + s2 + "-" + takeKDocs + "-" + takeKTerms + ".run";
            String runFile = outFileDir + "/" + outFile;

            new AspectRetQE(paraIndex, catalogIndex, passageRanking, runFile, stopWordsFile, queryIdToNameMapFile,
                    omitQueryTerms, takeKTerms, takeKDocs, analyzer, similarity);
        }  else if (command.equalsIgnoreCase("catalog-ret")) {
            String indexDir = args[1];
            String passageRun = args[2];
            String runFile = args[3];
            new CatalogRetrieval(indexDir, passageRun, runFile);
        } else if (command.equalsIgnoreCase("support-psg-qe")) {
            String s1 = null, s2;

            String paraIndex = args[1];
            String catalogIndex = args[2];
            String passageRanking = args[3];
            String runFileDir = args[4];
            String stopWordsFile = args[5];
            String queryIdToNameMapFile = args[6];
            boolean omitQueryTerms = args[7].equalsIgnoreCase("yes");
            int takeKTerms = Integer.parseInt(args[8]);
            String analyzer = args[9];
            String similarity = args[10];

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

            String outFile = "SupportPsgQE" + "-" + s1 + "-" + s2 + "-" + takeKTerms + ".run";
            String runFile = runFileDir + "/" + outFile;

            new SupportPsgQE(paraIndex, catalogIndex, passageRanking, runFile, stopWordsFile, queryIdToNameMapFile, omitQueryTerms,
                    takeKTerms, analyzer, similarity);
        } else if (command.equalsIgnoreCase("support-psg-aspect-link-prf")) {
            String paraIndex = args[1];
            String passageRanking = args[2];
            String runFileDir = args[3];

            String outFile = "SupportPsgAspectLinkPRF.run";
            String runFile = runFileDir + "/" + outFile;

            new SupportPsgAspectLinkPRF(paraIndex, passageRanking, runFile);
        } else if (command.equalsIgnoreCase("index-aspect-linked-corpus")) {
            System.out.println("Indexing aspect links.");
            String corpusDir = args[1];
            String indexDir = args[2];
            new IndexAspectLinkedCarCorpus(corpusDir, indexDir);
        } else if (command.equalsIgnoreCase("index-catalog")) {
            String catalog = args[1];
            String indexDir = args[2];
            try {
                new IndexCatalog(catalog, indexDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (command.equalsIgnoreCase("create-association-file")) {
            String indexDir = args[1];
            String outFile = args[2];
            Set<String> runFiles = new HashSet<>(Arrays.asList(Arrays.copyOfRange(args, 2, args.length)));
            new CreateAssociationFile(indexDir, runFiles, outFile);
        } else if (command.equalsIgnoreCase("marginalize")) {
            String supportPassageRunFile = args[1];
            String candidatePassageRunFile = args[2];
            String newPassageRunFile = args[3];
            new Marginalize(supportPassageRunFile, candidatePassageRunFile, newPassageRunFile);
        } else if (command.equalsIgnoreCase("rra")) {
            String runFileDir = args[1];
            String outFile = args[2];
            new ReciprocalRankAggregation(runFileDir, outFile);
        }
    }
}
