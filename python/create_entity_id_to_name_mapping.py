import sys
from typing import Dict, Any
import time
import tqdm
import argparse
import os
from trec_car import read_data

count = 0
total = 29794697


def create_database(corpus: str, save: str):
    with open(corpus, 'rb') as cbor:
        id_to_name_dict: Dict[str, str] = dict(
            (body.pageid, body.page)
            for para in tqdm.tqdm(read_data.iter_paragraphs(cbor), total=total)
            for body in para.bodies if isinstance(body, read_data.ParaLink)

        )

    write_to_file(id_to_name_dict, save)

    print('File written to: {}'.format(save))


def write_to_file(query_dict: Dict[str, str], output: str) -> None:
    with open(output, 'w') as f:
        for key, value in query_dict.items():
            f.write("%s\t%s\n" % (key, value))


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Create a mappings from entity_id to entity_name.")
    parser.add_argument("--corpus", help="Path to the directory storing corpus.", required=True)
    parser.add_argument("--save", help="Path to the output directory.", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])
    create_database(args.corpus, args.save)


if __name__ == '__main__':
    main()

