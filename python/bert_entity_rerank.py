import csv
from typing import Dict, List
import argparse
import sys
import json
import tqdm
import gensim
from scipy import spatial
import numpy as np
import pandas as pd
import operator
from sqlitedict import SqliteDict

from bert_serving.client import BertClient

bc = BertClient()


def load_run_file(file_path: str) -> Dict[str, List[str]]:
    rankings: Dict[str, List[str]] = {}
    with open(file_path, 'r') as file:
        for line in file:
            line_parts = line.split(" ")
            queryId = line_parts[0]
            entityId = line_parts[2]
            entity_list: List[str] = []
            if queryId in rankings.keys():
                entity_list = rankings[queryId]
            entity_list.append(entityId)
            rankings[queryId] = entity_list
    return rankings


def load_query_annotations(query_annotation_file: str) -> pd.DataFrame:
    return pd.read_csv(query_annotation_file)


def load_entity_id_to_name_file(entity_id_to_name_file: str) -> pd.DataFrame:
    return pd.read_csv(entity_id_to_name_file, sep='\t', names=['EntityId', 'EntityName'])


def get_query_annotations(query: str, df: pd.DataFrame) -> Dict[str, float]:
    annotations = json.loads(df[df['Query'] == query]['Annotations'].values[0])
    return dict(
        (ann['entity_name'], ann['score'])
        for ann in annotations
    )


def get_entity_name(entity_id: str, df: pd.DataFrame) -> str:
    return df[df['EntityId'] == entity_id]['EntityName'].values[0]


def aggregate_embedding(arrays: List, method: str) -> np.ndarray:
    if method == 'mean':
        return np.mean(np.array(arrays), axis=0)
    elif method == 'sum':
        return np.sum(np.array(arrays), axis=0)


def bert_entity_embedding(entity: str, vec: np.ndarray, method: str) -> np.ndarray:
    num = num_words(entity)
    entity_embeddings = [vec[i] for i in range(1, num + 1)]
    return aggregate_embedding(entity_embeddings, method)


def get_query_entity_embeddings(query_entities, method: str) -> Dict[str, np.ndarray]:
    query_entity_embeddings: Dict[str, np.ndarray] = {}
    for entity in query_entities:
        # Get BERT embeddings
        vec = bc.encode([entity])
        bert_emb = bert_entity_embedding(entity, vec[0], method)  # Embedding for entity
        query_entity_embeddings[entity] = bert_emb
    return query_entity_embeddings


def num_words(s: str) -> int:
    return len(s.split())


def cosine_similarity(query_entity: str,
                      target_entity: str,
                      method: str,
                      id_to_name: pd.DataFrame) -> float:
    if target_entity in id_to_name.values:
        target_entity_name = get_entity_name(target_entity, id_to_name)
    else:
        target_entity_name = target_entity[target_entity.index(':') + 1:].replace('%20', ' ')

    # Get BERT embeddings
    vec = bc.encode([query_entity, target_entity_name])
    qe_bert = bert_entity_embedding(query_entity, vec[0], method)  # Embedding for query_entity
    te_bert = bert_entity_embedding(query_entity, vec[1], method)  # Embedding for target_entity

    return 1 - spatial.distance.cosine(qe_bert, te_bert)


def get_target_entity_embedding(target_entity: str, id_to_name: pd.DataFrame, method: str) -> np.ndarray:
    if target_entity in id_to_name.values:
        target_entity_name = get_entity_name(target_entity, id_to_name)
    else:
        target_entity_name = target_entity[target_entity.index(':') + 1:].replace('%20', ' ')

    # Get BERT embeddings
    vec = bc.encode([target_entity_name])
    te_bert = bert_entity_embedding(target_entity_name, vec[0], method)  # Embedding for target_entity
    return te_bert


def entity_score(query_annotations: Dict[str, float],
                 query_entity_embeddings: Dict[str, np.ndarray],
                 target_entity: str,
                 method: str,
                 id_to_name: pd.DataFrame) -> float:
    score = 0
    te_emb = get_target_entity_embedding(target_entity, id_to_name, method)
    for query_entity in query_annotations.keys():
        conf = query_annotations[query_entity]
        qe_emb = query_entity_embeddings[query_entity]
        sim = 1 - spatial.distance.cosine(qe_emb, te_emb)
        score += sim * conf
    return score


def re_rank(run_dict: Dict[str, List[str]],
            query_annotations: pd.DataFrame,
            method: str,
            id_to_name: pd.DataFrame,
            out_file: str) -> None:
    for query in tqdm.tqdm(run_dict.keys(), total=len(run_dict)):
        ranked_entities: Dict[str, float] = rank_entities_for_query(run_dict[query],
                                                                    get_query_annotations(query,
                                                                                          query_annotations),
                                                                    method,
                                                                    id_to_name)
        if not ranked_entities:
            print('Empty ranking for query: {}'.format(query))
        else:
            run_file_strings: List[str] = to_run_file_strings(query, ranked_entities)
            write_to_file(run_file_strings, out_file)


def rank_entities_for_query(entity_list: List[str],
                            query_annotations: Dict[str, float],
                            method: str,
                            id_to_name: pd.DataFrame) -> Dict[str, float]:
    query_entity_embeddings: Dict[str, np.ndarray] = get_query_entity_embeddings(query_annotations.keys(), method)
    ranking: Dict[str, float] = dict(
        (entity, entity_score(query_annotations, query_entity_embeddings, entity, method, id_to_name))
        for entity in entity_list
    )

    return dict(sorted(ranking.items(), key=operator.itemgetter(1), reverse=True))


def to_run_file_strings(query: str, entity_ranking: Dict[str, float]) -> List[str]:
    run_file_strings: List[str] = []
    rank: int = 1
    for entity, score in entity_ranking.items():
        run_file_string: str = query + ' Q0 ' + entity + ' ' + str(rank) + ' ' + str(score) + ' BERT-ReRank'
        run_file_strings.append(run_file_string)
        rank += 1

    return run_file_strings


def write_to_file(run_file_strings: List[str], run_file: str) -> None:
    with open(run_file, 'a') as f:
        for item in run_file_strings:
            f.write("%s\n" % item)


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Entity re-ranking using BERT-as-a-service.")
    parser.add_argument("--run", help="TREC CAR entity run file to re-rank.", required=True)
    parser.add_argument("--query-annotations", help="File containing TagMe annotations for queries.", required=True)
    parser.add_argument("--aggr-method", help="Aggregation method for embeddings (mean|sum).", required=True)
    parser.add_argument("--entity-id-to-name-file", help="Sqlite file containing entity id to name mappings.",
                        required=True)
    parser.add_argument("--output", help="Output run file (re-ranked).", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])

    print('Loading run file...')
    run_dict: Dict[str, List[str]] = load_run_file(args.run)
    print('[Done].')

    print('Loading query annotations...')
    query_annotations: pd.DataFrame = load_query_annotations(args.query_annotations)
    print('[Done].')

    print('Loading entity id to name mappings...')
    id_to_name: pd.DataFrame = load_entity_id_to_name_file(args.entity_id_to_name_file)
    print('[Done].')

    print("Re-Ranking run...")
    re_rank(run_dict, query_annotations, args.aggr_method, id_to_name, args.output)
    print('[Done].')

    print('New run file written to {}'.format(args.output))


if __name__ == '__main__':
    main()
