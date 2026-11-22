#!/usr/bin/env python3
"""从老师干净提交生成独立部署源码副本，原目录和已有副本均不覆盖。"""
import argparse
import io
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEACHER_COMMIT = '990cea2cc3686599ef5068945706a13e7e0afc5b'


def prepare(source, target):
    """固定来源和补丁顺序，目标必须是忽略目录下尚不存在的新目录。"""
    target = target.resolve()
    if not target.is_relative_to((ROOT / '.local').resolve()) or target.exists():
        raise ValueError('NEW_PRIVATE_TARGET_REQUIRED')
    head = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    dirty = subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True)
    if head != TEACHER_COMMIT or dirty.strip():
        raise ValueError('TEACHER_SOURCE_MISMATCH')
    content = subprocess.check_output(['git', '-C', str(source), 'archive', '--format=zip', head])
    target.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        archive.extractall(target)
    for name in ('course-local-fixes.patch', 'course-deployment-fixes.patch'):
        patch = ROOT / 'integrations/course' / name
        subprocess.run(['git', 'apply', '--check', str(patch)], cwd=target, check=True)
        subprocess.run(['git', 'apply', str(patch)], cwd=target, check=True)
    (target / '.dockerignore').write_text(
        '**\n!backend/\n!backend/pom.xml\n!backend/src/\n!backend/src/main/\n!backend/src/main/**\n'
        '!web/\n!web/**\n**/node_modules/\n**/dist/\n**/target/\n**/.env.local\n', encoding='utf-8')
    return {'teacherCommit': head, 'target': str(target), 'strategy': 'ddd'}


def main():
    """显式指定老师仓库和新副本，不修改或部署任何远端服务。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--target', required=True, type=Path)
    args = parser.parse_args()
    print(prepare(args.source, args.target))


if __name__ == '__main__':
    main()
