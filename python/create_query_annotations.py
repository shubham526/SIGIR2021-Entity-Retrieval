import tagme
from typing import Dict, List, Any, Tuple
import argparse
import sys
import json
import tqdm


# Set the authorization token for subsequent calls.
tagme.GCUBE_TOKEN = "####" #YOUR TOKEN HERE


def write_file(data: List[Tuple[str, List[Any]]], out_file: str) -> None:
    with open(out_file, 'a') as f:
        for item in data:
            query_id: str = item[0]
            query_entities: List[Any] = item[1]
            f.write("%s\t%s\n" % (query_id, json.dumps(query_entities)))


def annotate(id_to_name: Dict[str, str], out_file: str) -> None:
    query_list: List[str] = list(id_to_name.keys())
    data: List[Tuple[str, List[Any]]] = [
        to_query_dict(query_id, id_to_name[query_id])
        for query_id in tqdm.tqdm(query_list, total=len(query_list))
    ]
    write_file(data, out_file)


def to_query_dict(query_id: str, query_name: str) -> Tuple[str, Any]:
    query_dict: Tuple[str, List[json]] = (
        query_id, get_entities(query_name)
    )
    return query_dict


def to_entity_dict(entity) -> json:
    entity_dict: Dict[str, Any] = {
        'begin': entity.begin,
        'end': entity.end,
        'entity_id': entity.entity_id,
        'entity_name': entity.entity_title,
        'score': entity.score,
        'mention': entity.mention,
    }
    return json.dumps(entity_dict)


def get_entities(query_name: str) -> List[Any]:
    query_annotations = tagme.annotate(query_name).get_annotations()
    entity_list: List[Any] = [to_entity_dict(query_entity) for query_entity in query_annotations]
    return entity_list


def read_tsv(file: str) -> Dict[str, str]:
    res: Dict[str, str] = {}
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
    parser = argparse.ArgumentParser("Annotate TREC CAR queries using TagMe. Annotations are stored in TSV format.")
    parser.add_argument("--queries", help="File containing TREC CAR queries.", required=True)
    parser.add_argument("--save", help="Output file containing query annotations.", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])

    print('Loading entity id to name mappings...')
    id_to_name: Dict[str, str] = read_tsv(args.queries)
    print('[Done].')

    annotate(id_to_name, args.save)


if __name__ == '__main__':
    main()
