import sys
from typing import Dict, Any, List, Tuple
import time
import tqdm
import argparse
import os
import json
from trec_car import read_data


def create_mapping(outlines_file: str, save: str, query_model: str) -> None:
    query_dict: Dict[str, str] = {}

    with open(outlines_file, 'rb') as outlines:
        for page in tqdm.tqdm(read_data.iter_outlines(outlines)):
            if query_model == 'title':
                query_id, query_name = build_section_query_str(page, [])
                query_dict[query_id] = query_name
            else:
                for section_path in page.flat_headings_list():
                    query_id, query_name = build_section_query_str(page, section_path)
                    query_dict[query_id] = query_name

    # Write to file
    write_to_file(query_dict, save)

    print('File written to: {}'.format(save))


def build_section_query_str(page: read_data.Page, section_path: List[read_data.Section]) -> Tuple[Any, Any]:
    if not section_path:
        return page.page_id, page.page_name.replace("\t", " ")
    else:
        query_name: List[str] = [page.page_name]
        query_id: List[str] = [page.page_id]
        for section in section_path:
            query_name.append(section.heading)
            query_id.append(section.headingId)
        return '/'.join(query_id), ' '.join(query_name).replace("\t", " ")


def write_to_file(data: Dict[str, str], output_file: str) -> None:
    with open(output_file, 'w') as f:
        for key, value in data.items():
            f.write("%s\t%s\n" % (key, value))


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Create a mapping of TREC CAR query ids to names. Store file in TSV format.")
    parser.add_argument("--outlines", help="Outlines file.", required=True)
    parser.add_argument("--save", help="Output file.", required=True)
    parser.add_argument("--query-model", help="Query model (title|section).", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])
    create_mapping(args.outlines_file, args.save, args.query_model)


if __name__ == '__main__':
    main()

