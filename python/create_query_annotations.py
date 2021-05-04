import csv
import tagme
from typing import Dict, List, Any
import argparse
import sys
import json
import tqdm
import pandas as pd

# Set the authorization token for subsequent calls.
tagme.GCUBE_TOKEN = "8775ecea-90d0-4fca-89d3-e19c0790489f-843339462"


def write_file(data, out_file):
    fields = ['Query', 'Annotations']
    # writing to csv file
    with open(out_file, 'w') as csv_file:
        # creating a csv dict writer object
        writer = csv.DictWriter(csv_file, fieldnames=fields)

        # writing headers (field names)
        writer.writeheader()

        # writing data rows
        writer.writerows(data)


def annotate(topics_file: str, id_to_name: pd.DataFrame, out_file: str) -> None:
    query_list = get_query_list(topics_file)
    data = [to_query_dict(query, id_to_name) for query in tqdm.tqdm(query_list, total=len(query_list))]
    write_file(data, out_file)


def get_query_list(filename: str) -> List[str]:
    return [line.rstrip('\n') for line in open(filename)]


def to_query_dict(query: str, df: pd.DataFrame) -> Dict[str, Any]:
    query_dict: Dict[str, Any] = {
        'Query': query,
        'Annotations': get_entities(get_query_name(query, df))
    }
    return query_dict


def to_entity_dict(entity):
    entity_dict: Dict[str, Any] = {
        'begin': entity.begin,
        'end': entity.end,
        'entity_id': entity.entity_id,
        'entity_name': entity.entity_title,
        'score': entity.score,
        'mention': entity.mention,
    }
    return entity_dict


def get_entities(query: str) -> str:
    query_annotations = tagme.annotate(query).get_annotations()
    entity_list = [to_entity_dict(query_entity) for query_entity in query_annotations]
    return json.dumps(entity_list)


def load_id_to_name_file(id_to_name_file: str) -> pd.DataFrame:
    return pd.read_csv(id_to_name_file, sep='\t', names=['QueryId', 'QueryName'])


def get_query_name(query_id: str, df: pd.DataFrame) -> str:
    return df[df['QueryId'] == query_id]['QueryName'].values[0]


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Annotate TREC CAR queries using TagMe. Annotations are stored in CSV format.")
    parser.add_argument("--topics-file", help="File containing TREC CAR queries.", required=True)
    parser.add_argument("--id-to-name-file", help="File containing TREC CAR queries.", required=True)
    parser.add_argument("--output-file", help="Name of the output (CSV) file.", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])

    print('Loading entity id to name mappings...')
    id_to_name: pd.DataFrame = load_id_to_name_file(args.id_to_name_file)
    print('[Done].')

    annotate(args.topics_file, id_to_name, args.output_file)


if __name__ == '__main__':
    main()
