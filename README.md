# Entity Retrieval Using Fine-Grained Entity Aspects
Shubham Chatterjee and Laura Dietz. 2021. Entity Retrieval Using Fine-Grained Entity Aspects. In _Proceedings of the 44th International ACM SIGIR Conference on Research and Development in Information Retrieval (SIGIR ’21)._

This is an online appendix for the paper. This repository contains
- code associated with this paper and the instructions on how to execute the code. 
- additional resources developed for this paper, including an aspect-linked version of the corpus from [TREC Complex Answer Retrieval (CAR)](http://trec-car.cs.unh.edu/). 

Shield: [![CC BY-SA 4.0][cc-by-sa-shield]][cc-by-sa]

All data associated with this work is licensed and released under a
[Creative Commons Attribution-ShareAlike 4.0 International License][cc-by-sa].

[![CC BY-SA 4.0][cc-by-sa-image]][cc-by-sa]

[cc-by-sa]: http://creativecommons.org/licenses/by-sa/4.0/
[cc-by-sa-image]: https://licensebuttons.net/l/by-sa/4.0/88x31.png
[cc-by-sa-shield]: https://img.shields.io/badge/License-CC%20BY--SA%204.0-lightgrey.svg

## 1. Downloads
1. **Datasets.** We use two datasets in our work:
- **TREC CAR.** Click [here](http://trec-car.cs.unh.edu/datareleases/) to download the official TREC CAR benchmarks. The experiments in the paper are based on the following two benchmarks: `benchmarkY1-train` and `benchmarkY2-test`. Please download these datasets and the associated qrels. 
- **Entity Aspect Linking.** Click [here](https://www.cs.unh.edu/~dietz/eal-dataset-2020/entity-aspect-linking-2020.html) to download the EAL dataset.
2. **Aspect Linked Corpus.** Click [here](https://www.cs.unh.edu/~dietz/eal-linked-wiki-paragraphs-v0.9/) for more information on entity aspects and to download the aspect-linked TREC CAR corpus used in this work.
3. **Our features and other data**
Click [here](https://unh.box.com/s/oj9bsxlfl5cwusi9iboo61rib1di3lhd) to download a tar file which contains the passage rankings, entity rankings, support passage rankings, various ground truth files, feature files, etc. used in this work. 

## 2. Necessary Installations
To run the code, the following libraries need to be installed:
1. **TREC CAR Tools.** The TREC CAR data can be read using the official `trec-car-tools`. The python code for creating QueryId/EntityId to QueryName/EntityName mappings requires `trec-car-tools` to read the TREC CAR data. It can be installed via pip as follows: `pip install trec-car-tools`. For more information, [read the documentation](https://trec-car-tools.readthedocs.io/en/latest/), and [see the Github repository](https://github.com/TREMA-UNH/trec-car-tools).
2. **TagMe Entity Linker.** We use the TagMe entity linker to entity link the queries. It can be installed via pip as follows: `pip install tagme`. For more details, [read the documentation](https://pypi.org/project/tagme/), [see the demo](https://tagme.d4science.org/tagme/), and [read the paper](https://dl.acm.org/doi/pdf/10.1145/1871437.1871689?casa_token=dUr_eu7goxQAAAAA%3A5dwXjRtjVbwFHfVfaGc3o56VqiqHbdNGQGHthth1KoQYUxmH1uF_VPUaw2H_IFfoZX-FdlkqgKk).
3. **BERT-As-A-Service.** We use `bert-as-service` to obtain embeddings of entities using BERT. For installation instructions, [read the documentation](https://bert-as-service.readthedocs.io/en/latest/index.html).

The code is partially in Java and partially in Python. Hence, you would also need both Java and Python installed on your system. The code has been tested using **Java openJDK 13** and **Python 3.7**. To install the java code, you need Maven 3. We use Lucene v8.7.0 in this work.

**Install Java Code.** To install the java code: 
- Clone this repository using `git clone`. 
- Inside the repository, there are two folders: java (containing the java code) and python (containing the python code). From the java directory, run the following command: `mvn clean install`. This should create a jar file called `SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar` inside `java/target`.

The python scripts have a help function which may be called using the `--help` option with then script. For example, `python bert_entity_rerank.py --help`.

## 3. Create the initial resources
Before running the code to produce the runs files (features), we need several resources such as an index of all paragraphs in the TREC CAR corpus, index of all aspects in the aspect catalog, etc. Below, we show how to create these resources. 

### 3.1. Create Lucene index of aspect-linked TREC CAR corpus
Download the aspect linked TREC CAR corpus from the link above. An index of this corpus can be created as follows:
```java
java -jar SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar index-corpus $path_to_corpus_dir $index
```
The index created above contains the following information: 
- `Id`: ParagraphIds of the paragraphs.
- `Text`: Text of the paragraphs.
- `Entities`: JSON-encoded strings representing entities in a paragraph. A JSON-string contains the following information: the anchor text of the entity link (`mention`), the id of the Wikipedia page pointed to by the entity link (`linkPageId`), the name of the Wikipedia page pointed to by the entity link (`linkPageName`), and the aspect of the entity in the paragraph (`aspect`).

**Reading the TREC CAR aspect linked corpus in Java.** The TREC CAR data can be read using the official `trec-car-tools` available [here](https://github.com/TREMA-UNH/trec-car-tools). Below is a code snippet in Java to read the aspect-linked corpus. For the corresponding Python code, see the documentation of `trec-car-tools`. 

```java
 BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath));
 for(Data.Paragraph paragraph : DeserializeData.iterableParagraphs(bis)) {
 
     // Get the entities in the paragraph
     List<String> entityList = getEntities(paragraph);
     
     // Do something else
 }
```

```java
 /**
   * Method to return all entities in a paragraph.
   * @param paragraph Represents a Wikipedia paragraph with entity links preserved.
   * @return List of entities in the paragraph 
   */
   List<String> getEntities(@NotNull Data.Paragraph paragraph) {
        List<String> entityList = new ArrayList<>();
        
        // Iterate over the body of the paragraph
        for (Data.ParaBody body : paragraph.getBodies()) {
        
            // If you found a link
            if (body instanceof Data.ParaLink) {
                Data.ParaLink paraLink = (Data.ParaLink) body;
                
                // Get the linked section, that is, entity aspect
                String linkSection = paraLink.getLinkSection();
                
                // Get the anchor text of the link
                String anchorText = paraLink.getAnchorText();
                
                // Get the Id of the Wikipedia page
                String pageId = paraLink.getPageId();
                
                // Get the title of the Wikipedia page
                String pageName = paraLink.getPage();
                
                // We put everything into a JSON Object
                JSONObject entity = new JSONObject();
                entity.put("mention", anchorText);
                entity.put("linkPageId", pageId);
                entity.put("linkPageName", pageName);
                entity.put("aspect", linkSection);
                entityList.add(entity.toJSONString());
            }
        }
        return entityList;
    }
```

### 3.2. Create a Lucene index of the aspect catalog
The aspect calatog can be found in the EAL dataset (link above). An index of this catalog can be created as follows:
```
java -jar SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar index-catalog $path_to_catalog_dir $index
```
The index created above contains the following information: 
- `Id`: AspectIds of the aspects.
- `Name`: Plain string representation of the aspect name.
- `Text`: Text of the aspect, i.e., the text from the section on the Wikipedia page of the linked entity.
- `Entities`: JSON-encoded strings representing entities in the aspect. 

### 3.3. Create a TSV file of mappings from QueryIds/EntityIds to QueryNames/EntityNames
The corresponding python scripts can be used for this purpose.
```
python3 create_query_id_to_name_mapping.py --outlines $outlines_file --output $output_file --query-model $query_model
python3 create_entity_id_to_name_mapping.py --corpus $paragraph_cbor --save $output_file 
```
The `outlines_file` is available from the TREC CAR dataset. We need to use the outlines file corresponding to the benchmark we are using. For example, for **BenchmarkY1-Train**, the outlines file to be used is **train.pages.cbor-outlines.cbor**. The **query_model** should be either `title` (for page-level queries) or `section` (for section-level queries). The **paragraph_cbor** is the `paragraphCorpus` available with the TREC CAR dataset. 

Note that the above scripts writes the output in a TSV format, hence the file names should end with `.tsv`. 

### 3.4. Create a TSV file of query annotations
The baselines _Wiki2Vec-ReRank_ and _BERT-ReRank_ in the paper need entity link annotations for the queries. We use TagMe entity linker for this purpose.
```
python3 create_query_annotations.py --queries $queries_file --save $output_file
```
The `queries_file` is the QueryId to QueryName mapping file created above. The `output_file` is a `.tsv` file.

### 3.5. Create an entity support passage run file
```
java -jar SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar make-support-psg-run $index $passage_run $entity_run $output_dir $parallel
```
- `index`: Index of CAR paragraphs (created above).
- `passage run`: Paragraph run file in TREC format.
- `entity run`: Entity run file in TREC format.
- `output_dir`: Directory where output run file will be written.
- `parallel`: Whether to run the code in parallel. Use `-Djava.util.concurrent.ForkJoinPool.common.parallelism=N` to set the number of threads.

## 4. Create the run files
In this work, we create several run files and use them as features in a Learning-To-Rank framework  (see below). Below, we show how to create the run files used in this work.
### 4.1. Baselines
1. **Wiki2Vec-ReRank.** Re-implementation of the entity ranking method from [Gerritse et al., 2020](https://arxiv.org/abs/2005.02843). 
```
python3 wiki2vec-rerank.py --run $run --annotations $query_annotations --wiki2vec $wiki2vec_embeddings --entity-id-to-name $entity_id_to_name_file --top-k $K --save $output_file
```
- `run`: TREC CAR entity run file to re-rank.
- `query_annotations`: Queries annotated using TagMe
- `wiki2vec_embeddings`: Embeddings of entities obtained using Wikipedia2Vec. We use the Wikipedia2Vec entity embeddings with graph component made availabel by Gerritse et al. [here](https://github.com/informagi/GEEER). For more information on Wikipedia2Vec, [read the paper](https://aclanthology.org/2020.emnlp-demos.4.pdf) and [read the documentation](https://wikipedia2vec.github.io/wikipedia2vec/).
- `entity_id_to_name_file`: File containing mappings from EntityIds to EntityNames.
- `K`: To-K entities to rerank.
- `output_file`: Re-ranked run file.

2. **BERT-ReRank.** Entity re-ranking using [`bert-as-service`](https://github.com/hanxiao/bert-as-service).
```
python3 wiki2vec-rerank.py --run $run --annotations $query_annotations --aggr-method $aggregation_method --entity-id-to-name $entity_id_to_name_file --top-k $K --save $output_file
```
- `aggregation_method`: Aggregation method for entities. Can be either `mean` or `sum`. In this work, we use `mean`. 

3. **CatalogRetrieval.** 
```
java -jar SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar catalog-ret $catalog_index $query_id_to_name_file $save_dir $aspect_to_entity_file $top_k
```
- `catalog_index`: Path to the ctalog index.
- `query_id_to_name_file`: File containing mappings from QueryIds to QueryNames.
- `save_dir`: Directory where run file would be saved.
- `aspect_to_entity_file`: File containing mapping from EntityAspectIs to EntityId.
- `top_k`: Top-K aspects for each query.

## Learning-to-rank and ENT-Rank
- We perform our learning-to-rank experiments using the toolkit `ranklips`. Read about it [here](https://www.cs.unh.edu/~dietz/rank-lips/).
- We use an easy-to-use ranklips version of [ENT-Rank](https://www.cs.unh.edu/~dietz/appendix/ent-rank/) called `ent-ranklips`. Read about it [here](https://www.cs.unh.edu/~dietz/ent-rank-lips/). 


## Cite 
```
@inproceedings{chatterjee2021entity,
  author = {Chatterjee, Shubham and Dietz, Laura},
  title = {Entity Retrieval Using Fine-Grained Entity Aspects},
  year = {2021},
  publisher = {Association for Computing Machinery},
  address = {New York, NY, USA},
  url = {https://doi.org/10.1145/3404835.3463035},
  doi = {10.1145/3404835.3463035},
  booktitle = {Proceedings of the 44th International ACM SIGIR Conference on Research and Development in Information Retrieval},
  numpages = {5},
  location = {Virtual Event, Canada},
  series = {SIGIR '21}
}
```

## Contact
If you have any questions, please contact Shubham Chatterjee at <sc1242@wildcats.unh.edu> or <shubham.chatterjee94@gmail.com>.  
