"""只读提取老师权限清单，生成可双击HTML加载的本地原型资源，不改Excel。"""
from argparse import ArgumentParser
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json
import posixpath
import xml.etree.ElementTree as ET


def extract(source):
    namespace = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    with ZipFile(source) as archive:
        strings = [''.join(item.itertext()) for item in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('m:si', namespace)]
        relations = {item.attrib['Id']: item.attrib['Target'] for item in ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))}
        sheet = ET.fromstring(archive.read('xl/workbook.xml')).find('m:sheets/m:sheet', namespace)
        target = relations[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
        part = target.lstrip('/') if target.startswith('/') else posixpath.normpath('xl/' + target)
        cells = {}
        for cell in ET.fromstring(archive.read(part)).findall('m:sheetData/m:row/m:c', namespace):
            value = cell.find('m:v', namespace)
            if value is not None:
                cells[cell.attrib['r']] = strings[int(value.text)] if cell.attrib.get('t') == 's' else value.text
    role_names = [cells[column + '2'] for column in 'EFGHI']
    systems, features, normalizations, missing_rights = [], [], [], []
    current_system = current_module = None
    for row in sorted(int(key[1:]) for key in cells if key.startswith('C') and key[1:].isdigit() and int(key[1:]) >= 3):
        if cells.get('A' + str(row)):
            current_system = {'id': len(systems) + 2, 'code': 'APP_' + str(len(systems) + 1).zfill(2),
                              'name': cells['A' + str(row)], 'sourceCell': 'A' + str(row), 'enabled': True}
            systems.append(current_system)
        if cells.get('B' + str(row)):
            current_module = cells['B' + str(row)]
        if current_system is None or current_module is None:
            raise ValueError('功能点缺少已核实系统/模块，拒绝猜测')
        rights = [cells.get(column + str(row)) for column in 'EFGHI']
        for column, value, role_name in zip('EFGHI', rights, role_names):
            if value is None:
                missing_rights.append({'cell': column + str(row), 'systemId': current_system['id'], 'feature': cells['C' + str(row)], 'role': role_name})
        normalized = []
        for column, value in zip('EFGHI', rights):
            tokens = []
            for token in (value.split('/') if value is not None else []):
                if token == 'view':
                    normalizations.append({'cell': column + str(row), 'source': 'view', 'display': '查看'})
                    token = '查看'
                if token not in ['无', '全部']:
                    tokens.append(token)
            normalized.append(tokens)
        operations = list(dict.fromkeys(token for tokens in normalized for token in tokens))
        features.append({'row': row, 'systemId': current_system['id'], 'module': current_module,
                         'name': cells['C' + str(row)], 'description': cells.get('D' + str(row), ''),
                         'roleRights': rights, 'operations': operations,
                         'operationRoles': [[index for index, value in enumerate(rights) if value == '全部' or operation in normalized[index]]
                                            for operation in operations]})
    return {'sourceFile': source.name, 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'sheet': sheet.attrib['name'], 'roleNames': role_names, 'systems': systems, 'features': features,
            'moduleCount': len({(item['systemId'], item['module']) for item in features}),
            'normalizations': normalizations, 'missingRights': missing_rights, 'apiMappingsProvided': False}


if __name__ == '__main__':
    repository = Path(__file__).resolve().parents[1]
    parser = ArgumentParser()
    parser.add_argument('--source', type=Path, default=repository / '市政公司应用软件功能权限清单.xlsx')
    parser.add_argument('--output', type=Path, default=repository / 'prototype/applications.js')
    arguments = parser.parse_args()
    data = extract(arguments.source)
    content = '/** 由scripts/build-prototype-applications.py只读提取；来源摘要和原始矩阵保留在数据中。 */\n'
    content += "(function () {\n'use strict';\nconst source = " + json.dumps(data, ensure_ascii=False, indent=2) + ';\n'
    content += "if (typeof module !== 'undefined' && module.exports) module.exports = source;\nelse globalThis.MunicipalApplicationSource = source;\n})();\n"
    arguments.output.write_text(content, encoding='utf-8')
    print(json.dumps({'systems': len(data['systems']), 'modules': data['moduleCount'], 'features': len(data['features']),
                      'normalizations': data['normalizations'], 'missingRights': data['missingRights'], 'originalWorkbookUnchanged': True}, ensure_ascii=False))
