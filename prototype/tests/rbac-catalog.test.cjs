const test = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { createCatalog } = require('../catalog.js');

test('原型与正式默认目录：25个操作的编码、中文名称和真实接口逐项一致', () => {
  const source = readFileSync(path.resolve(__dirname, '../../backend/src/main/java/com/municipal/rbaccore/service/port/security/RbacCatalog.java'), 'utf8');
  const definitions = new Map([...source.matchAll(/definitions\.put\(\s*"([^"]+)"\s*,\s*List\.of\(route\("([^"]+)"\s*,\s*"([^"]+)"\)\)/g)].map(match => [match[1], match[2] + ' ' + match[3]]));
  const names = new Map([...source.matchAll(/Map\.entry\("([^"]+)"\s*,\s*"([^"]+)"\)/g)].map(match => [match[1], match[2]]));
  const catalog = createCatalog();
  const buttons = catalog.permissions.filter(item => item.systemId === 1 && item.type === 'BUTTON');
  assert.equal(definitions.size, 25);
  assert.equal(buttons.length, 25);
  for (const button of buttons) {
    assert.equal(button.apiIds.length, 1, button.code);
    const api = catalog.apis.find(item => item.id === button.apiIds[0]);
    assert.equal(definitions.get(button.code), api.method + ' ' + api.path, button.code);
    assert.equal(names.get(button.code), button.name, button.code);
  }
  const menuNames = new Map([...source.matchAll(/menus\.put\("([^"]+)"\s*,\s*"([^"]+)"\)/g)].map(match => [match[1], match[2]]));
  const menus = catalog.permissions.filter(item => item.systemId === 1 && item.type === 'MENU');
  assert.equal(menus.length, 5);
  for (const menu of menus) assert.equal(menuNames.get(menu.code), menu.name, menu.code);
});
