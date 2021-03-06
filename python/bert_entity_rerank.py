from typing import Dict, List
import argparse
import sys
import json
import tqdm
from scipy import spatial
import numpy as np
import operator
from bert_serving.client import BertClient

bc = BertClient()


def load_run_file(file_path: str) -> Dict[str, List[str]]:
    rankings: Dict[str, List[str]] = {}
    with open(file_path, 'r') as file:
        for line in file:
            line_parts = line.split(" ")
            query_id = line_parts[0]
            entity_id = line_parts[2]
            entity_list: List[str] = rankings[query_id] if query_id in rankings.keys() else []
            entity_list.append(entity_id)
            rankings[query_id] = entity_list
    return rankings


def get_query_annotations(query_annotations: str) -> Dict[str, float]:
    annotations = json.loads(query_annotations)
    res: Dict[str, float] = {}
    for ann in annotations:
        a = json.loads(ann)
        res[a['entity_name']] = a['score']
    return res


def aggregate_embedding(arrays: List, method: str) -> np.ndarray:
    if method == 'mean':
        return np.mean(np.array(arrays), axis=0)
    elif method == 'sum':
        return np.sum(np.array(arrays), axis=0)


def bert_entity_embedding(entity: str, vec: np.ndarray, method: str) -> np.ndarray:
    num: int = num_words(entity)
    entity_embeddings: List[np.ndarray] = [vec[i] for i in range(1, num + 1)]
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


def get_target_entity_embedding(target_entity: str, id_to_name: Dict[str, str], method: str) -> np.ndarray:
    if target_entity in id_to_name.keys():
        target_entity_name = id_to_name[target_entity]
    else:
        # If the name is not found in the dict, then extract it from the id.
        # This is BAD idea!
        # But its a hack!
        target_entity_name = target_entity[target_entity.index(':') + 1:].replace('%20', ' ')

    # Get BERT embeddings
    vec = bc.encode([target_entity_name])
    te_bert = bert_entity_embedding(target_entity_name, vec[0], method)  # Embedding for target_entity
    return te_bert


def entity_score(
        query_annotations: Dict[str, float],
        query_entity_embeddings: Dict[str, np.ndarray],
        target_entity: str,
        method: str,
        id_to_name: Dict[str, str]
) -> float:
    score = 0
    te_emb = get_target_entity_embedding(target_entity, id_to_name, method)
    for query_entity, conf in query_annotations.items():
        qe_emb = query_entity_embeddings[query_entity]
        distance = 1 - spatial.distance.cosine(qe_emb, te_emb)
        score += distance * conf
    return score


def re_rank(
        run_dict: Dict[str, List[str]],
        query_annotations: Dict[str, str],
        method: str,
        id_to_name: Dict[str, str],
        k: int,
        out_file: str
) -> None:
    print('Re-ranking top-{} entities from the run file.'.format(k))
    for query_id, query_entities in tqdm.tqdm(run_dict.items(), total=len(run_dict)):
        ranked_entities: Dict[str, float] = rank_entities_for_query(
            entity_list=query_entities[:k],
            query_annotations=get_query_annotations(query_annotations[query_id]),
            method=method,
            id_to_name=id_to_name
        )
        if not ranked_entities:
            print('Empty ranking for query: {}'.format(query_id))
        else:
            run_file_strings: List[str] = to_run_file_strings(query_id, ranked_entities)
            write_to_file(run_file_strings, out_file)


def rank_entities_for_query(
        entity_list: List[str],
        query_annotations: Dict[str, float],
        method: str,
        id_to_name: Dict[str, str]
) -> Dict[str, float]:
    query_entity_embeddings: Dict[str, np.ndarray] = get_query_entity_embeddings(query_annotations.keys(), method)
    ranking: Dict[str, float] = dict(
        (entity, entity_score(
            query_annotations=query_annotations,
            query_entity_embeddings=query_entity_embeddings,
            target_entity=entity,
            method=method,
            id_to_name=id_to_name
        ))
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


def read_tsv(file: str) -> Dict[str, str]:
    res = {}
    with open(file, 'r') as f:
        for line in f:
            parts = line.split('\t')
            key = parts[0]
            value = parts[1]
            res[key] = value
    return res


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Entity re-ranking using pre-trained BERT embeddings.")
    parser.add_argument("--run", help="TREC CAR entity run file to re-rank.", required=True)
    parser.add_argument("--annotations", help="File containing TagMe annotations for queries.", required=True)
    parser.add_argument("--aggr-method", help="Aggregation method for embeddings (mean|sum).", required=True)
    parser.add_argument("--entity-id-to-name", help="File containing mappings from TREC CAR entityIds to entity names.",
                        required=True)
    parser.add_argument("--top-k", help="Top K entities to re-rank from run file.", required=True)
    parser.add_argument("--save", help="Output run file (re-ranked).", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])

    print('Loading run file...')
    run_dict: Dict[str, List[str]] = load_run_file(args.run)
    print('[Done].')

    print('Loading query annotations...')
    query_annotations: Dict[str, str] = read_tsv(args.annotations)
    print('[Done].')

    print('Loading entity id to name mappings...')
    id_to_name: Dict[str, str] = read_tsv(args.entity_id_to_name)
    print('[Done].')

    print("Re-Ranking run...")
    re_rank(run_dict, query_annotations, args.aggr_method, id_to_name, int(args.top_k), args.save)
    print('[Done].')

    print('New run file written to {}'.format(args.save))


if __name__ == '__main__':
    main()

