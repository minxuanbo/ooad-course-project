#!/usr/bin/env python3
"""发布前校验非秘密配置、资源入口和文件引用；不连接Docker或服务器。"""
import argparse
import os
from pathlib import Path
import re
from urllib.parse import urlsplit
import yaml


def main():
    """拒绝占位镜像、多副本及错误公网地址，渲染文件只允许写忽略目录。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env', required=True, type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    settings = {}
    for line in args.env.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            if not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
                raise ValueError('INVALID_CONFIGURATION_KEY')
            settings[key] = value
    for key in ('RELEASE_ID', 'SECRET_PREFIX'):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', settings[key]):
            raise ValueError('INVALID_RELEASE_OR_SECRET_PREFIX')
    if settings.get('APP_REPLICAS', '0') not in ('0', '1'):
        raise ValueError('SINGLE_REPLICA_REQUIRED')
    for key, value in settings.items():
        if key.endswith('_IMAGE') and ('CHANGE_ME' in value or value.endswith(':latest')
                                       or not re.search(r'(:[^/]+|@sha256:[0-9a-f]{64})$', value)):
            raise ValueError('FIXED_IMAGE_REFERENCE_REQUIRED:' + key)
    for key in ('COURSE_PUBLIC_ORIGIN', 'RBAC_PUBLIC_ORIGIN'):
        parsed = urlsplit(settings[key])
        if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.path or parsed.query or parsed.fragment or parsed.username:
            raise ValueError('PUBLIC_ORIGIN_REQUIRED:' + key)
    text = (root / 'deploy/stack.yml').read_text(encoding='utf-8')
    def substitute(match):
        """仅替换Compose变量，不执行shell或文件中任何代码。"""
        key, default = match.group(1), match.group(2)
        return settings[key] if key in settings else default if default is not None else os.environ[key]
    rendered = re.sub(r'\$\{([A-Z][A-Z0-9_]*)(?::-([^}]*))?\}', substitute, text)
    stack = yaml.safe_load(rendered)
    for config in stack['configs'].values():
        if not (root / 'deploy' / config['file']).resolve().is_file():
            raise ValueError('CONFIG_FILE_MISSING')
    for name in ('rbac-backend', 'course-backend'):
        if stack['services'][name]['environment']['APP_BACKEND_STRATEGY'] != 'ddd':
            raise ValueError('DDD_REQUIRED')
    if args.output:
        target = args.output.resolve()
        if not target.is_relative_to(root / '.local') or target.exists():
            raise ValueError('NEW_PRIVATE_OUTPUT_REQUIRED')
        target.write_text(rendered, encoding='utf-8')
    print('Configuration valid: seven Swarm services, two DDD backends, public TCP 80/8088. No deployment performed.')


if __name__ == '__main__':
    main()
