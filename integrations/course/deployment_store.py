"""独立课程业务审计的MySQL存储；不读写RBAC鉴权日志。"""
from contextlib import closing
from datetime import datetime, timezone
import json

import pymysql


class AuditConflict(ValueError):
    """同一应用的幂等键被不同操作者或不同内容重复使用。"""


class MysqlAuditStore:
    """只访问显式的审计库和只读课程连接，每次操作独立提交或回滚。"""

    def __init__(self, audit_connection, course_connection):
        self.audit_connection = audit_connection
        self.course_connection = course_connection

    def ping(self):
        """检查持久审计表已按首次SQL准备，不在产品启动时自动建表。"""
        with closing(self.audit_connection()) as connection, connection.cursor() as cursor:
            cursor.execute('SELECT id FROM audit_events LIMIT 1')

    def write(self, system, actor, payload, context=None):
        """服务端覆盖主体和时间，唯一键保证并发重放只有一条持久记录。"""
        for key in ('requestId', 'type', 'objectType', 'objectId', 'target'):
            limit = 255 if key == 'requestId' else 1000
            if not isinstance(payload.get(key), str) or not 1 <= len(payload[key]) <= limit:
                raise ValueError('INVALID_AUDIT_FIELD')
        if any(not isinstance(payload.get(key), dict) for key in ('before', 'after')):
            raise ValueError('INVALID_AUDIT_SNAPSHOT')
        canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        response = {'at': datetime.now(timezone.utc).isoformat(), 'actor': actor,
                    **{key: payload[key] for key in ('type', 'target', 'before', 'after')},
                    'termId': None, 'courseId': None, 'classId': None, **(context or {})}
        with closing(self.audit_connection()) as connection:
            try:
                with connection.cursor() as cursor:
                    cursor.execute('INSERT INTO audit_events '
                                   '(app_code,request_id,actor_id,payload,response,occurred_at,target) '
                                   'VALUES(%s,%s,%s,%s,%s,%s,%s)',
                                   (system, payload['requestId'], actor['id'], canonical, '{}',
                                    datetime.fromisoformat(response['at']).replace(tzinfo=None), payload['target']))
                    response['id'] = cursor.lastrowid
                    cursor.execute('UPDATE audit_events SET response=%s WHERE id=%s',
                                   (json.dumps(response, ensure_ascii=False), response['id']))
                connection.commit()
                return response
            except pymysql.IntegrityError as error:
                connection.rollback()
                if error.args[0] != 1062:
                    raise
                with connection.cursor() as cursor:
                    cursor.execute('SELECT actor_id,payload,response FROM audit_events '
                                   'WHERE app_code=%s AND request_id=%s', (system, payload['requestId']))
                    existing = cursor.fetchone()
                if existing and existing['actor_id'] == actor['id'] and existing['payload'] == canonical:
                    return json.loads(existing['response'])
                raise AuditConflict('AUDIT_IDEMPOTENCY_CONFLICT') from None

    def query(self, system, params):
        """数据库内分页与过滤，同一事务快照返回总数及当前页，始终限定应用。"""
        page = int(params.get('page', ['1'])[0])
        size = int(params.get('pageSize', ['100'])[0])
        if page < 1 or page > 1000000 or not 1 <= size <= 100:
            raise ValueError('INVALID_PAGE')
        where, values = ['app_code=%s'], [system]
        for key, column in (('actorId', 'actor_id'), ('from', 'occurred_at'), ('to', 'occurred_at')):
            value = params.get(key, [''])[0]
            if value:
                if key == 'actorId':
                    value = int(value)
                else:
                    value = datetime.fromisoformat(value.replace('Z', '+00:00'))
                    if value.tzinfo is None:
                        raise ValueError('TIMEZONE_REQUIRED')
                    value = value.astimezone(timezone.utc).replace(tzinfo=None)
                where.append(column + (' >= %s' if key == 'from' else ' <= %s' if key == 'to' else ' = %s'))
                values.append(value)
        for key, expression in (('actor', "JSON_UNQUOTE(JSON_EXTRACT(response,'$.actor.name'))"), ('target', 'target')):
            value = params.get(key, [''])[0]
            if value:
                where.append('LOCATE(LOWER(%s),LOWER(' + expression + ')) > 0')
                values.append(value)
        clause = ' AND '.join(where)
        with closing(self.audit_connection()) as connection, connection.cursor() as cursor:
            cursor.execute('SELECT COUNT(*) AS total FROM audit_events WHERE ' + clause, values)
            total = cursor.fetchone()['total']
            cursor.execute('SELECT response FROM audit_events WHERE ' + clause
                           + ' ORDER BY id DESC LIMIT %s OFFSET %s', values + [size, (page - 1) * size])
            items = [json.loads(row['response']) for row in cursor.fetchall()]
        return {'page': page, 'pageSize': size, 'total': total, 'items': items}

    def object_context(self, payload):
        """参数化只读查询补充课程对象归属，课程规则不进入RBAC本体。"""
        import re
        match = re.fullmatch(r'cpm-(term|course|class|group|milestone|submission|oral_record)-(\d+)', payload['objectId'])
        if not match:
            return {}
        kind, ident = match.groups()
        queries = {
            'term': 'SELECT id AS termId,NULL AS courseId,NULL AS classId FROM term WHERE id=%s',
            'course': 'SELECT term_id AS termId,id AS courseId,NULL AS classId FROM course WHERE id=%s',
            'class': 'SELECT c.term_id AS termId,c.id AS courseId,t.id AS classId FROM teaching_class t JOIN course c ON c.id=t.course_id WHERE t.id=%s',
            'group': 'SELECT c.term_id AS termId,c.id AS courseId,g.responsible_class_id AS classId FROM project_group g JOIN course c ON c.id=g.course_id WHERE g.id=%s',
            'milestone': 'SELECT c.term_id AS termId,c.id AS courseId,NULL AS classId FROM milestone m JOIN course c ON c.id=m.course_id WHERE m.id=%s',
            'submission': 'SELECT c.term_id AS termId,c.id AS courseId,NULL AS classId FROM submission s JOIN milestone m ON m.id=s.milestone_id JOIN course c ON c.id=m.course_id WHERE s.id=%s',
            'oral_record': 'SELECT c.term_id AS termId,c.id AS courseId,NULL AS classId FROM oral_assessment a JOIN milestone m ON m.id=a.milestone_id JOIN course c ON c.id=m.course_id WHERE a.id=%s',
        }
        with closing(self.course_connection()) as connection, connection.cursor() as cursor:
            cursor.execute(queries[kind], (int(ident),))
            row = cursor.fetchone()
        prefixes = {'termId': 'term', 'courseId': 'course', 'classId': 'class'}
        return {key: 'cpm-' + prefixes[key] + '-' + str(value)
                for key, value in (row or {}).items() if value is not None}
