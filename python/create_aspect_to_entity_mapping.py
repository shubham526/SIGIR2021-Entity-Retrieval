from typing import Dict, Any
import time
import tqdm
import argparse
import os
import sys
from trec_car import read_data


def create_mapping(corpus_dir: str, save: str):
    aspect_to_entity_dict: Dict[str, str] = {}
    file_list = os.listdir(corpus_dir)
    for file_name in file_list:
        file_path = os.path.join(corpus_dir, file_name)
        get_mapping(file_path, aspect_to_entity_dict)

    print('Writing to file...')
    write_to_file(aspect_to_entity_dict, save)
    print('File written to: {}'.format(save))


def get_mapping(file_path: str, aspect_to_entity_dict: Dict[str, str]):
    with open(file_path, 'rb') as cbor:
        for para in tqdm.tqdm(read_data.iter_paragraphs(cbor), total=10000):
            for body in para.bodies:
                if isinstance(body, read_data.ParaLink) and body.link_section is not None:
                    aspect_to_entity_dict[body.link_section] = body.pageid


def write_to_file(query_dict: Dict[str, str], output: str) -> None:
    with open(output, 'w') as f:
        for key, value in query_dict.items():
            f.write("%s\t%s\n" % (key, value))


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Create a mappings from aspect_id to entity_id.")
    parser.add_argument("--corpus-dir", help="Path to the directory storing corpus.", required=True)
    parser.add_argument("--save", help="Path to the output directory.", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])
    create_mapping(args.corpus_dir, args.save)


if __name__ == '__main__':
    main()
