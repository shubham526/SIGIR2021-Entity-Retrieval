package help;

import edu.unh.cs.treccar_v2.Data;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LuceneHelper {
    @NotNull
    @Contract("_, _ -> new")
    public static Analyzer getAnalyzer(String analyzerStr, @NotNull List<String> indexFields) {

        Analyzer textAnalyzer = ("std".equals(analyzerStr))? new StandardAnalyzer():
                ("english".equals(analyzerStr)? new EnglishAnalyzer(): new StandardAnalyzer());


        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        for (String field : indexFields) {
            if (! field.equalsIgnoreCase("Text")) {
                fieldAnalyzers.put(field, new WhitespaceAnalyzer());
            }
        }
        return new PerFieldAnalyzerWrapper(textAnalyzer, fieldAnalyzers);
    }

    @Nullable
    public static Similarity getSimilarity(@NotNull String similarityStr) {

        if (similarityStr.equalsIgnoreCase("bm25")) {
            return new BM25Similarity();
        } else if (similarityStr.equalsIgnoreCase("lmds")) {
            return new LMDirichletSimilarity(1500);
        } else if (similarityStr.equalsIgnoreCase("lmjm")) {
            return new LMJelinekMercerSimilarity(0.5f);
        }
        return null;

    }

    @NotNull
    public static IndexSearcher createSearcher(String indexDir, @NotNull String similarityStr) {
        Similarity similarity = getSimilarity(similarityStr);

        Directory dir = null;
        try {
            dir = FSDirectory.open((new File(indexDir).toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(dir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert reader != null;
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        return searcher;
    }
    @NotNull
    @Contract("_, _ -> new")
    public static QueryParser createParser(String field, Analyzer analyzer) {
        return new QueryParser(field, analyzer);
    }

    public static TopDocs searchIndex(BooleanQuery booleanQuery,
                                      int n,
                                      @NotNull IndexSearcher searcher)throws IOException {
        return searcher.search(booleanQuery, n);
    }
    @Nullable
    public static Document searchIndex(String field, String query, @NotNull IndexSearcher searcher)throws IOException, ParseException {
        Term term = new Term(field,query);
        Query q = new TermQuery(term);
        TopDocs tds = searcher.search(q,1);

        ScoreDoc[] retDocs = tds.scoreDocs;
        if(retDocs.length != 0) {
            return searcher.doc(retDocs[0].doc);
        }
        return null;
    }

    @NotNull
    public static String buildSectionQueryStr(@NotNull Data.Page page, @NotNull List<Data.Section> sectionPath) {
        if(sectionPath.isEmpty()) {
            System.err.println("Warning: sectionPath is empty. This QueryStringBuilder is identical to TitleQueryStringBuilder");
            return page.getPageName().replaceAll("\t", "s");
        } else {
            StringBuilder queryStr = new StringBuilder();
            queryStr.append(page.getPageName());
            for (Data.Section section : sectionPath) {
                queryStr.append(" ").append(section.getHeading());
            }
            return queryStr.toString().replaceAll("\t", "s");
        }
    }




}
