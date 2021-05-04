import csv
from typing import Dict, List
import argparse
import sys
import json
import tqdm
import gensim
from scipy import spatial
import pandas as pd
import operator


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


def load_embeddings(embedding_file: str) -> gensim.models.KeyedVectors:
    return gensim.models.KeyedVectors.load(embedding_file, mmap='r')


def get_query_annotations(query: str, df: pd.DataFrame) -> Dict[str, float]:
    annotations = json.loads(df[df['Query'] == query]['Annotations'].values[0])
    return dict(
        (ann['entity_name'], ann['score'])
        for ann in annotations
    )


def get_entity_name(entity_id, df):
    return df[df['EntityId'] == entity_id]['EntityName'].values[0]


def in_vocab(entity: str, model) -> bool:
    return True if entity in model.vocab else False


def to_wiki2vec_entity(entity: str) -> str:
    return 'ENTITY/' + entity.replace(' ', '_')


def cosine_distance(query_entity: str,
                    target_entity: str,
                    wiki2vec: gensim.models.KeyedVectors,
                    id_to_name: pd.DataFrame) -> float:
    p_qe = to_wiki2vec_entity(query_entity)

    if target_entity in id_to_name.values:
        p_te = to_wiki2vec_entity(get_entity_name(target_entity, id_to_name))
    else:
        p_te = to_wiki2vec_entity(target_entity[target_entity.index(':') + 1:].replace('%20', ' '))

    # print("p_qe=" + p_qe)
    # print("p_te=" + p_te)

    if not in_vocab(p_qe, wiki2vec) or not in_vocab(p_te, wiki2vec):
        return 0.0

    emb_e1 = wiki2vec[p_qe]
    emb_e2 = wiki2vec[p_te]
    return 1 - spatial.distance.cosine(emb_e1, emb_e2)


def entity_score(query_entities: Dict[str, float],
                 target_entity: str,
                 wiki2vec: gensim.models.KeyedVectors,
                 id_to_name: pd.DataFrame) -> float:
    score = 0
    for query_entity in query_entities.keys():
        conf = query_entities[query_entity]
        distance = cosine_distance(query_entity, target_entity, wiki2vec, id_to_name)
        score += distance * conf
    return score


def re_rank(run_dict: Dict[str, List[str]],
            query_annotations: pd.DataFrame,
            wiki2vec: gensim.models.KeyedVectors,
            id_to_name: pd.DataFrame,
            out_file: str) -> None:
    for query in tqdm.tqdm(run_dict.keys(), total=len(run_dict)):
        ranked_entities: Dict[str, float] = rank_entities_for_query(run_dict[query][:500],
                                                                    get_query_annotations(query,
                                                                                          query_annotations),
                                                                    wiki2vec,
                                                                    id_to_name)
        if not ranked_entities:
            print('Empty ranking for query: {}'.format(query))
        else:
            run_file_strings: List[str] = to_run_file_strings(query, ranked_entities)
            write_to_file(run_file_strings, out_file)


def rank_entities_for_query(entity_list: List[str],
                            query_annotations: Dict[str, float],
                            wiki2vec: gensim.models.KeyedVectors,
                            id_to_name: pd.DataFrame) -> Dict[str, float]:
    ranking: Dict[str, float] = dict(
        (entity, entity_score(query_annotations, entity, wiki2vec, id_to_name))
        for entity in entity_list
    )

    return dict(sorted(ranking.items(), key=operator.itemgetter(1), reverse=True))


def to_run_file_strings(query: str, entity_ranking: Dict[str, float]) -> List[str]:
    run_file_strings: List[str] = []
    rank: int = 1
    for entity, score in entity_ranking.items():
        run_file_string: str = query + ' Q0 ' + entity + ' ' + str(rank) + ' ' + str(score) + ' Wiki2Vec-ReRank'
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
    parser = argparse.ArgumentParser("Entity re-ranking using Wiki2Vec. Re-implementation of (Gerritse et al., 2020).")
    parser.add_argument("--run", help="TREC CAR entity run file to re-rank.", required=True)
    parser.add_argument("--query-annotations", help="File containing TagMe annotations for queries.", required=True)
    parser.add_argument("--wiki2vec", help="Wiki2Vec file.", required=True)
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

    print('Loading entity embeddings...')
    wiki2vec: gensim.models.KeyedVectors = load_embeddings(args.wiki2vec)
    print('[Done].')

    print('Loading entity id to name mappings...')
    id_to_name: pd.DataFrame = load_entity_id_to_name_file(args.entity_id_to_name_file)
    print('[Done].')

    print("Re-Ranking run...")
    re_rank(run_dict, query_annotations, wiki2vec, id_to_name, args.output)
    print('[Done].')

    print('New run file written to {}'.format(args.output))


if __name__ == '__main__':
    main()
