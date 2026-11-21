#!/usr/bin/env python3
"""生成首次部署秘密文件；只写新忽略目录，不打印值，不自动写入Docker。"""
import argparse
import base64
import os
from pathlib import Path
import secrets

NAMES = ('mysql_root_password', 'rbac_db_password', 'course_db_password', 'audit_db_password',
         'course_read_password', 'jwt_secret', 'employee_password', 'admin_password')


def main():
    """数据库密码生成十六进制，员工与管理员仍分别配置、无写死默认口令。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True, type=Path)
    args = parser.parse_args()
    directory = args.directory.resolve()
    root = Path(__file__).resolve().parents[1]
    if not directory.is_relative_to(root / '.local') or directory.exists():
        raise ValueError('NEW_PRIVATE_DIRECTORY_REQUIRED')
    directory.mkdir(parents=True, mode=0o700)
    for name in NAMES:
        value = os.environ.get('RBAC_CORE_' + name.upper()) if name in ('employee_password', 'admin_password') else None
        if value is None:
            value = base64.b64encode(secrets.token_bytes(32)).decode() if name == 'jwt_secret' else secrets.token_hex(24)
        if not value or len(value.encode('utf-8')) > 72:
            raise ValueError('SECRET_LENGTH_INVALID')
        path = directory / name
        path.write_text(value, encoding='utf-8')
        path.chmod(0o600)
    print('Generated ' + str(len(NAMES)) + ' private files; values omitted. Docker secrets not created.')


if __name__ == '__main__':
    main()
