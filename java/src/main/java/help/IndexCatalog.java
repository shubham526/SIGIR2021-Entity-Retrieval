package help;


import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Create an index of the aspect catalog provided with the aspect linking dataset from CIKM 2020.
 * @version 1/20/2020
 * @author Shubham Chatterjee
 */

public class IndexCatalog {
    public IndexCatalog(String catalog, String indexDir) throws IOException {
        index(catalog, indexDir);
    }

    private void index(String catalog, String indexDir) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(catalog))));
        String line;
        JSONParser parser = new JSONParser();
        IndexWriter writer = createWriter(indexDir);
        ProgressBar pb = new ProgressBar("Progress",7893275 );
        while ((line = br.readLine()) != null) {
            try {
                JSONObject jsonObject = (JSONObject) parser.parse(line);
                JSONArray candidateAspects = (JSONArray) jsonObject.get("candidate_aspects");
                for (Object candidate : candidateAspects) {
                    JSONObject c = (JSONObject) candidate;
                    Document document = toLuceneDoc(c);
                    writer.addDocument(document);
                    pb.step();
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        writer.commit();
        writer.close();
        pb.close();
    }
    @NotNull
    private IndexWriter createWriter(String index)throws IOException {
        Directory indexDir = FSDirectory.open((new File(index)).toPath());
        Analyzer textAnalyzer = new EnglishAnalyzer();
        final Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("Id", new WhitespaceAnalyzer());
        fieldAnalyzers.put("Name", new WhitespaceAnalyzer());
        fieldAnalyzers.put("Entities", new WhitespaceAnalyzer());
        final DelegatingAnalyzerWrapper queryAnalyzer = new PerFieldAnalyzerWrapper(textAnalyzer, fieldAnalyzers);
        IndexWriterConfig conf = new IndexWriterConfig(queryAnalyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return new IndexWriter(indexDir, conf);
    }
    @NotNull
    private Document toLuceneDoc(@NotNull JSONObject candidate) {
        Document doc = new Document();
        String aspectId = candidate.get("aspect_id").toString();
        String aspectName = candidate.get("aspect_name").toString();
        JSONObject content = (JSONObject) candidate.get("aspect_content");
        String aspectContent = content.get("content").toString();
        String entityList = getEntities((JSONArray) content.get("entities"));

        doc.add(new StringField("Id", aspectId, Field.Store.YES));
        doc.add(new TextField("Name", aspectName, Field.Store.YES));
        doc.add(new TextField("Text", aspectContent, Field.Store.YES));
        doc.add(new TextField("Entities", entityList, Field.Store.YES));
        return doc;
    }

    @NotNull
    private String getEntities(@NotNull JSONArray entities) {
        List<String> entityList = new ArrayList<>();
        for (Object entity : entities) {
            entityList.add(((JSONObject) entity).toJSONString() );
        }
        return String.join("\n", entityList);
    }

    public static void main(@NotNull String[] args) throws IOException {
        String catalog = args[0];
        String indexDir = args[1];
        new IndexCatalog(catalog, indexDir);
    }
}
