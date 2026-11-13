const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const directory = path.resolve(__dirname, '..');

test('R18/R22 普通浏览器脚本模式无需模块加载器即可运行内存业务', () => {
  const context = vm.createContext({ crypto: globalThis.crypto, structuredClone });
  vm.runInContext(fs.readFileSync(path.join(directory, 'catalog.js'), 'utf8'), context);
  vm.runInContext(fs.readFileSync(path.join(directory, 'model.js'), 'utf8'), context);
  const model = context.MunicipalPrototype.createDemoModel();
  assert.equal(model.login('admin', context.MunicipalPrototype.DEMO_PASSWORD).username, 'admin');
  assert.equal(model.list().total, 168);
  assert.equal(model.tree().length, 15);
});
test('R18 离线入口仅加载同目录普通脚本和样式，无模块或远端依赖', () => {
  const html = fs.readFileSync(path.join(directory, 'index.html'), 'utf8');
  const scripts = [...html.matchAll(/<script\b([^>]+)>/g)].map(match => match[1]);
  assert.equal(scripts.length, 4);
  scripts.forEach(attributes => {
    assert.doesNotMatch(attributes, /type\s*=\s*["']module/);
    const asset = attributes.match(/src="([^"]+)"/)[1];
    assert.match(asset, /^\.\/[\w.-]+\.js$/);
    assert.ok(fs.existsSync(path.join(directory, asset)));
  });
  assert.ok(fs.existsSync(path.join(directory, 'styles.css')));
  for (const name of ['catalog.js', 'pages.js', 'app.js', 'model.js', 'styles.css']) {
    const source = fs.readFileSync(path.join(directory, name), 'utf8');
    assert.doesNotMatch(source, /\b(?:fetch|XMLHttpRequest|WebSocket|EventSource|sendBeacon|localStorage|sessionStorage)\s*[.(]/);
    assert.doesNotMatch(source, /(?:@import|url\()\s*["']?https?:\/\//);
    assert.doesNotMatch(source, /^\s*import\s/m);
  }
});
