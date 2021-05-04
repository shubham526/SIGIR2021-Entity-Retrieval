import sys
from typing import Dict, Any, List, Tuple
import time
import tqdm
import argparse
import os
import json
from trec_car import read_data
from sqlitedict import SqliteDict


def create_database(outlines_file: str, out_dir: str, out_file: str, save_format: str, query_model: str):
    output = out_dir + "/" + out_file + "." + save_format
    database = SqliteDict(output, autocommit=True)
    query_dict: Dict[str, str] = {}

    with open(outlines_file, 'rb') as outlines:
        for page in tqdm.tqdm(read_data.iter_outlines(outlines)):
            if query_model == 'title':
                query_id, query_name = build_section_query_str(page, [])
                if save_format == 'sqlite':
                    database[query_id] = query_name
                else:
                    query_dict[query_id] = query_name
            else:
                for section_path in page.flat_headings_list():
                    query_id, query_name = build_section_query_str(page, section_path)
                    if save_format == 'sqlite':
                        database[query_id] = query_name
                    else:
                        query_dict[query_id] = query_name

        if save_format == 'tsv':
            with open(output, 'w') as f:
                for key, value in query_dict.items():
                    f.write("%s\t%s\n" % (key, value))

    print('Database created at: {}'.format(output))


def create_mapping_in_jsonl_format(outlines_file: str, out_dir: str, out_file: str, query_model: str):
    output = out_dir + "/" + out_file
    f = open(output, 'w')
    with open(outlines_file, 'rb') as outlines:
        for page in tqdm.tqdm(read_data.iter_outlines(outlines)):
            if query_model == 'title':
                query_id, query_name = build_section_query_str(page, [])
                line = {
                    'query_id': query_id,
                    'query': query_name
                }
                f.write("%s\n" % json.dumps(line))

            else:
                for section_path in page.flat_headings_list():
                    query_id, query_name = build_section_query_str(page, section_path)
                    line = {
                        'query_id': query_id,
                        'query': query_name
                    }
                    f.write("%s\n" % json.dumps(line))

    f.close()
    print('File created at: {}'.format(output))


def build_section_query_str(page: read_data.Page, section_path: List[read_data.Section]) -> Tuple[Any, Any]:
    if not section_path:
        return page.page_id, page.page_name.replace("\t", "s")
    else:
        query_name: List[str] = [page.page_name]
        query_id: List[str] = [page.page_id]
        for section in section_path:
            query_name.append(section.heading)
            query_id.append(section.headingId)
        return '/'.join(query_id), ' '.join(query_name).replace("\t", "s")


def main():
    """
    Main method to run code.
    """
    parser = argparse.ArgumentParser("Create a database of mappings from query_id to query_name.")
    parser.add_argument("--outlines-file", help="Outlines file path.", required=True)
    parser.add_argument("--output-dir", help="Path to the output directory.", required=True)
    parser.add_argument("--output-file", help="Name of the database file.", required=True)
    parser.add_argument("--format", help="Format to save (tsv|sqlite).", required=True)
    parser.add_argument("--query-model", help="Query model (title|section).", required=True)
    args = parser.parse_args(args=None if sys.argv[1:] else ['--help'])
    create_database(args.outlines_file, args.output_dir, args.output_file, args.format, args.query_model)
    #create_mapping_in_jsonl_format(args.outlines_file, args.output_dir, args.output_file, args.query_model)


if __name__ == '__main__':
    main()
