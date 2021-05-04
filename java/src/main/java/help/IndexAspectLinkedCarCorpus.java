package help;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
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
import org.json.simple.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create an index of the TREC CAR corpus which is already aspect linked.
 * @version 1/22/2020
 * @author Shubham Chatterjee
 */

public class IndexAspectLinkedCarCorpus {
    private IndexWriter writer = null;
    private final ProgressBar pb;

    public IndexAspectLinkedCarCorpus(String pathToCorpus, String indexDir) {
        pb = new ProgressBar("Progress",29794697 );
        try {
            writer = createWriter(indexDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        createIndex(pathToCorpus);
    }

    private void createIndex(String pathToCorpus) {
       File corpusDir = new File(pathToCorpus);
       File[] files = corpusDir.listFiles();
        assert files != null;
        for (File file : files) {
           indexFile(file);
        }
        try {
            writer.commit();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        pb.close();

    }

    private void indexFile(@NotNull File file) {
        String filePath = file.getAbsolutePath();
        BufferedInputStream bis = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(filePath));
            for(Data.Paragraph paragraph : DeserializeData.iterableParagraphs(bis)) {
                Document document = toLuceneDoc(paragraph);
                try {
                    writer.addDocument(document);
                    pb.step();
                } catch (IOException | NullPointerException e) {
                    e.printStackTrace();
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if(bis != null) {
                    bis.close();
                    writer.commit();
                } else {
                    System.out.println("Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    @NotNull
    private IndexWriter createWriter(String index)throws IOException {
        Directory indexDir = FSDirectory.open((new File(index)).toPath());
        Analyzer textAnalyzer = new EnglishAnalyzer();
        final Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("Id", new WhitespaceAnalyzer());
        fieldAnalyzers.put("Entities", new WhitespaceAnalyzer());
        final DelegatingAnalyzerWrapper queryAnalyzer = new PerFieldAnalyzerWrapper(textAnalyzer, fieldAnalyzers);
        IndexWriterConfig conf = new IndexWriterConfig(queryAnalyzer);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return new IndexWriter(indexDir, conf);
    }

    @NotNull
    private Document toLuceneDoc(@NotNull Data.Paragraph paragraph) {
        Document doc = new Document();
        String paraId = paragraph.getParaId();
        String paraText = paragraph.getTextOnly();
        String entityList = getEntities(paragraph);
        doc.add(new StringField("Id", paraId, Field.Store.YES));
        doc.add(new TextField("Text", paraText, Field.Store.YES));
        doc.add(new TextField("Entities", entityList, Field.Store.YES));
        return doc;


    }

    @NotNull
    private String getEntities(@NotNull Data.Paragraph paragraph) {
        List<String> entityList = new ArrayList<>();
        for (Data.ParaBody body : paragraph.getBodies()) {
            if (body instanceof Data.ParaLink) {
                Data.ParaLink paraLink = (Data.ParaLink) body;
                String linkSection = paraLink.getLinkSection();
                String anchorText = paraLink.getAnchorText();
                String pageId = paraLink.getPageId();
                String pageName = paraLink.getPage();
                JSONObject entity = new JSONObject();
                entity.put("mention", anchorText);
                entity.put("linkPageId", pageId);
                entity.put("linkPageName", pageName);
                entity.put("aspect", linkSection);
                entityList.add(entity.toJSONString());
            }
        }
        return String.join("\n", entityList);
    }

    public static void main(@NotNull String[] args) {
        String corpusDir = args[0];
        String indexDir = args[1];
        new IndexAspectLinkedCarCorpus(corpusDir, indexDir);
    }
}
