import csv
from typing import Dict, List
import argparse
import sys
import json
import tqdm
import gensim
from scipy import spatial
import operator


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


def load_embeddings(embedding_file: str) -> gensim.models.KeyedVectors:
    return gensim.models.KeyedVectors.load(embedding_file, mmap='r')


def get_query_annotations(query_annotations: str) -> Dict[str, float]:
    annotations = json.loads(query_annotations)
    res: Dict[str, float] = {}
    for ann in annotations:
        a = json.loads(ann)
        res[a['entity_name']] = a['score']
    return res


def in_vocab(entity: str, model) -> bool:
    return True if entity in model.key_to_index.keys() else False


def to_wiki2vec_entity(entity: str) -> str:
    return 'ENTITY/' + entity.replace(' ', '_')


def cosine_distance(
        query_entity: str,
        target_entity: str,
        wiki2vec: gensim.models.KeyedVectors,
        id_to_name: Dict[str, str]
) -> float:

    # The code fails for this entity in the annotation because the entity is in plural but the embedding is for
    # the singular entity. I don't know how to fix this and don't want to spend more time on this.
    # So this is a hack!
    if query_entity == 'Antibiotics':
        query_entity = 'Antibiotic'

    p_qe = to_wiki2vec_entity(query_entity)

    if target_entity in id_to_name.keys():
        p_te = to_wiki2vec_entity(id_to_name[target_entity]).strip()
    else:
        p_te = to_wiki2vec_entity(target_entity[target_entity.index(':') + 1:].replace('%20', ' '))

    if not in_vocab(p_qe, wiki2vec) or not in_vocab(p_te, wiki2vec):
        return 0.0

    emb_e1 = wiki2vec[p_qe]
    emb_e2 = wiki2vec[p_te]
    return 1 - spatial.distance.cosine(emb_e1, emb_e2)


def entity_score(
        query_entities: Dict[str, float],
        target_entity: str,
        wiki2vec: gensim.models.KeyedVectors,
        id_to_name: Dict[str, str]
) -> float:
    score = 0
    for query_entity, conf in query_entities.items():
        distance = cosine_distance(query_entity, target_entity, wiki2vec, id_to_name)
        score += distance * conf
    return score


def re_rank(
        run_dict: Dict[str, List[str]],
        query_annotations: Dict[str, str],
        wiki2vec: gensim.models.KeyedVectors,
        id_to_name: Dict[str, str],
        k: int,
        out_file: str
) -> None:

    print('Re-ranking top-{} entities from the run file.'.format(k))
    for query_id, query_entities in tqdm.tqdm(run_dict.items(), total=len(run_dict)):
        ranked_entities: Dict[str, float] = rank_entities_for_query(
            query_entities[:k],
            get_query_annotations(query_annotations[query_id]),
            wiki2vec,
            id_to_name
        )
        if not ranked_entities:
            print('Empty ranking for query: {}'.format(query))
        else:
            run_file_strings: List[str] = to_run_file_strings(query_id, ranked_entities)
            write_to_file(run_file_strings, out_file)


def rank_entities_for_query(
        entity_list: List[str],
        query_annotations: Dict[str, float],
        wiki2vec: gensim.models.KeyedVectors,
        id_to_name: Dict[str, str]
) -> Dict[str, float]:
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
    parser = argparse.ArgumentParser("Entity re-ranking using Wiki2Vec. Re-implementation of (Gerritse et al., 2020).")
    parser.add_argument("--run", help="TREC CAR entity run file to re-rank.", required=True)
    parser.add_argument("--annotations", help="File containing TagMe annotations for queries.", required=True)
    parser.add_argument("--wiki2vec", help="Wiki2Vec file.", required=True)
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

    print('Loading entity embeddings...')
    wiki2vec: gensim.models.KeyedVectors = load_embeddings(args.wiki2vec)
    print('[Done].')

    print('Loading entity id to name mappings...')
    id_to_name: Dict[str, str] = read_tsv(args.entity_id_to_name)
    print('[Done].')

    print("Re-Ranking run...")
    re_rank(run_dict, query_annotations, wiki2vec, id_to_name, int(args.top_k), args.save)
    print('[Done].')

    print('New run file written to {}'.format(args.save))


if __name__ == '__main__':
    main()

