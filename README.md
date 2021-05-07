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

## Downloads
1. **Datasets.** We use two datasets in our work:
- **TREC CAR.** Click [here](http://trec-car.cs.unh.edu/datareleases/) to download the official TREC CAR benchmarks. The experiments in the paper are based on the following two benchmarks: `benchmarkY1-train` and `benchmarkY2-test`. Please download these datasets and the associated qrels. 
- **Entity Aspect Linking.** Click [here](https://www.cs.unh.edu/~dietz/eal-dataset-2020/entity-aspect-linking-2020.html) to download the EAL dataset.
2. **Aspect Linked Corpus.** Click [here](https://unh.box.com/s/arsnh3s7jk2jbrfzb5d7buhhjkwovooj) to download the aspect-linked TREC CAR corpus used in this work. We use the `paragraphCorpus` provided with the TREC CAR dataset. However, the corpus on the official TREC CAR website does not contain entity aspect links; it contains only entity links. Hence, we have aspect-linked the entire corpus using the aspect linker from [Ramsdell et al., 2020](https://dl.acm.org/doi/10.1145/3340531.3412875).
3. **Our features and other data**
Click [here](https://unh.box.com/s/oj9bsxlfl5cwusi9iboo61rib1di3lhd) to download a tar file which contains the passage rankings, entity rankings, support passage rankings, various ground truth files, feature files, etc. used in this work. 

## Description of the aspect linked corpus
The aspect linked corpus is in the same format as the original TREC CAR corpus. The paragraphs are CBOR-encoded to preserve the entity links contained therein. Each entity link is represented by four components: 
- Target Page: Name of the Wikipedia page which it links to.
- Target PageId: Id of the Wikipedia page which it links to.
- Link Section: Section (aspect) of the Wikipedia page it links to. This is the aspect link.
- Anchor Text: Anchor text of the link. 

You can read the full description of the data on the official web page of TREC CAR [here](http://trec-car.cs.unh.edu/).

## Running the code
The code is partially in Java and partially in Python. The code has been tested using Java openJDK 13 and Python 3.7. The java code required Maven 3 to be installed. 

To install the java code: 
- Clone this repository using `git clone`. 
- Inside the repository, there are two folders: java (containing the java code) and python (containing the python code). From the java directory, run the following command: `mvn clean install`. This should create a jar file called `SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar` inside `java/target`.

You may now run this jar file using `java -jar SIGIR2021-Short-Final-Code-Release-1.0-SNAPSHOT-jar-with-dependencies.jar` along with various command line arguments (see below)  to create the features for training. 

The folder "python" contains scripts for the following:
1. `bert_entity_rerank.py`: Entity re-ranking using [BERT-as-a-service](https://github.com/hanxiao/bert-as-service).
2. `create-page-id-to-name-mapping.py`: Script to create a mapping from TREC CAR entity-ids to entity-names.
3. `create_query_annotations.py`: Script to annotate TREC CAR queries using TagMe.
4. `entity_rerank.py`: Re-implementation of the entity ranking method from [Gerritse et al., 2020](https://arxiv.org/abs/2005.02843).

The python scripts have a help function which may be called using the `--help` option with then script. For example, `python entity_rerank.py --help`. 

## Reading the TREC CAR data
The TREC CAR data can be read using the official `trec-car-tools` available [here](https://github.com/TREMA-UNH/trec-car-tools). We use the Java version in this work.

## Learning-to-rank and ENT-Rank
- We perform our learning-to-rank experiments using the toolkit `ranklips`. Read about it [here](https://www.cs.unh.edu/~dietz/rank-lips/).
- We use an easy-to-use ranklips version of [ENT-Rank](https://www.cs.unh.edu/~dietz/appendix/ent-rank/) called `ent-ranklips`. Click [here](https://unh.box.com/s/wof0pu754koqqinj8hggvweu8v9yw01a) to download a tar file containing a Haskell binary and a PDF describing its use. 


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
