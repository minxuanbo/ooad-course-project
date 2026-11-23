import test from 'node:test'
import assert from 'node:assert/strict'
import { authorizationResultLabel } from '../src/lib/presentation.js'

test('R16 权限查询、身份认证和请求鉴权采用各自结果语义', () => {
  assert.equal(
    authorizationResultLabel({ eventType: 'PERMISSION_CHECK', allowed: false, httpStatus: 200 }),
    '无权限',
  )
  assert.equal(authorizationResultLabel({ eventType: 'PERMISSION_CHECK', allowed: true }), '有权限')
  assert.equal(authorizationResultLabel({ eventType: 'LOGIN_SUCCESS', allowed: true }), '认证成功')
  assert.equal(authorizationResultLabel({ eventType: 'LOGIN_FAILED', allowed: false }), '认证失败')
  assert.equal(
    authorizationResultLabel({ eventType: 'AUTHORIZATION', allowed: true, httpStatus: 409 }),
    '允许',
  )
  assert.equal(authorizationResultLabel({ eventType: 'AUTHORIZATION', allowed: false }), '拒绝')
})
