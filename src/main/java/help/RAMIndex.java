package help;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to make a RAM index.
 * This class uses the Lucene 7.7.0 RAMDirectory to create in-memory indices.
 * NOTE: Use caution in the use of this class! The RAMDirectory class has been marked as deprecated by the developers.
 * @author Shubham Chatterjee
 * @version 03/11/2019
 */
public class RAMIndex {

    @NotNull
    public static Map<String, Float> searchIndex(String query, int n, IndexSearcher is, @NotNull QueryParser qp) {
        Map<String,Float> results = new HashMap<>();
        // Parse the query
        Query q = null;
        try {
            q = qp.parse(query);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        // Search the query
        TopDocs tds = null;
        try {
            tds = is.search(q,n);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Retrieve the results
        ScoreDoc[] retDocs;
        if (tds != null) {
            retDocs = tds.scoreDocs;
        } else {
            return results;
        }
        for (int i = 0; i < retDocs.length; i++) {
            try {
                Document doc = is.doc(retDocs[i].doc);
                float score = tds.scoreDocs[i].score;
                String id = doc.get("Id");
                results.put(id, score);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return results;
    }
    /**
     * Search the index for the given query and return top n hits.
     * The query is a Boolean Query which may consist of one or more terms queries.
     * @param query BooleanQuery Query to search
     * @param n Integer Top hits for the query
     * @return HashMap where Key = Document and Value = Score
     */
    @NotNull
    public static Map<String, Double> searchIndex(BooleanQuery query, int n, @NotNull IndexSearcher is) {
        Map<String, Double> results = new HashMap<>();

        // Search the query
        TopDocs tds = null;
        try {
            tds = is.search(query,n);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Retrieve the results
        ScoreDoc[] retDocs;
        if (tds != null) {
            retDocs = tds.scoreDocs;
        } else {
            return results;
        }
        for (int i = 0; i < retDocs.length; i++) {
            try {
                Document doc = is.doc(retDocs[i].doc);
                double score = tds.scoreDocs[i].score;
                String id = doc.get("Id");
                results.put(id, score);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    /**
     * Get the IndexWriter.
     * This method uses the lucene RAMDirectory which has been marked deprecated.
     * The reason for its use is that we want to maintain an in-memory index of relevant documents for
     * every query and there was no other tool in Lucene 7.7.0 that I am aware of that does this.
     * The problem with using RAMDirectory is that I cannot use parraelStreams in Java since this method
     * has been marked as not thread-safe by the developers.
     * @return IndexWriter
     */
    public static IndexWriter createWriter(Analyzer analyzer) {
        Directory dir = new RAMDirectory();
        IndexWriterConfig conf = new IndexWriterConfig(analyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter iw = null;
        try {
            iw = new IndexWriter(dir, conf);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return iw;

    }

    /**
     * Create a parser for the query.
     * @param field The field to search in the index
     * @param analyzer The analyzer to use
     * @return QueryParser
     */
    @NotNull
    @Contract("_, _ -> new")
    public static QueryParser createParser(String field, Analyzer analyzer) {
        return new QueryParser(field, analyzer);
    }
    /**
     * Get the IndexSearcher.
     * @return IndexSearcher
     * @throws IOException
     */
    @NotNull
    public static IndexSearcher createSearcher(Similarity similarity, @NotNull IndexWriter iw) throws IOException {
        Directory dir = iw.getDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        return searcher;
    }
    /**
     * Build an in-memory index of documents passed as parameters.
     * @param documents The documents to index
     */
    public static void createIndex(@NotNull List<Document> documents, IndexWriter iw) throws IOException {
        for (Document d : documents) {
            if (d != null) {
                try {
                    iw.addDocument(d);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        iw.commit();
    }

    /**
     * Close the directory to release the assciated memory.
     * @param iw IndexWriter
     * @throws IOException
     */
    public static void close(@NotNull IndexWriter iw) throws IOException {
        iw.getDirectory().close();

    }

    public static int numDocs(IndexWriter iw) {
        try {
            return  DirectoryReader.open(iw).numDocs();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

}

