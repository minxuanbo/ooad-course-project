#!/usr/bin/env python3
"""无需凭据的部署入口检查；不把页面可达冒充完整授权与业务验收。"""
import argparse
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main():
    """核查两个Nginx、RBAC应用身份和课程后端链，输出不含响应体或秘密。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rbac', default='http://114.116.254.25:8088')
    parser.add_argument('--course', default='http://114.116.254.25')
    args = parser.parse_args()
    checks = [(args.rbac, '/', 200), (args.course, '/', 200),
              (args.rbac, '/healthz', 200), (args.course, '/healthz', 200),
              (args.rbac, '/api/me', 401), (args.rbac, '/auth/me', 401),
              (args.rbac, '/course-identity/auth/me', 401),
              (args.course, '/api/me', 401)]
    result = []
    for base, path, expected in checks:
        try:
            response = urlopen(Request(base + path), timeout=15)
        except HTTPError as error:
            response = error
        with response:
            status = response.status
        result.append({'url': base + path, 'status': status, 'pass': status == expected})
    print(json.dumps(result, ensure_ascii=False))
    if not all(item['pass'] for item in result):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
