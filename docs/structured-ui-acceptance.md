# structured UI复验（2026-11-19）

本轮完成用户指定的structured模式UI复验与修复。基线为main `9c95e7a`，修复位于`codex/structured-ui-acceptance`。阶段6的ddd同项复验仍未执行，不将共用前端结果视为ddd业务链通过。

## 1. 环境与结果

Windows、Node 24.11.0、Playwright 1.58.2/Chromium、JDK 21.0.10、MySQL 8.4.13。后端运行`APP_BACKEND_STRATEGY=structured`，本轮未修改Java、API或数据库结构；沿用阶段5验收JAR，其后端源码与本分支一致。前端使用当前Vue源码和Vite开发代理。

最终回归在新建私有MySQL实例中执行，未复用前期调试库或万人导入库。初始夹具有32个虚构账号、35个角色、4个部门、15项权限、26项API，仅RBAC系统。额外权限和`/api/ui-fixture`目录项用于属性与映射测试，不是新增业务接口。每个账号使用独立BCrypt10盐。生成SQL、截图、原始结果和数据库均留在忽略目录`.local`。

| 检查 | 实际结果 |
| --- | --- |
| 真实浏览器场景 | 13项通过，失败/跳过0；单worker，最终运行23.2秒 |
| 原前端测试 | 26项通过，失败/跳过0 |
| 生产构建 | `npm run build`通过 |
| 布局 | 六个管理页面分别在390、540、768、1280 CSS像素检查，无文档级横向溢出；宽表在独立容器内滚动 |
| 人工桌面操作 | Chrome约540像素外窗完成登录、部门重名重试/新增/删除取消与确认、角色回车新建；数据库核对结果 |
| 图像复核 | 保存390/1280六页截图，抽查窄屏日志计数、窄屏资源确认弹窗及桌面人员表格 |

自动测试连接真实后端并回读保存结果。只有“完整集合/候选/写入失败重试”场景通过Playwright注入一次503；恢复后走真实接口。403范围变化、409部门重名及最后维护者拒绝均为真实响应。没有以接口直调替代页面提交结果。

## 2. 修复与复验覆盖

三个问题均先获得失败证据，再修复：

1. 移动端`.toolbar span { display:none }`隐藏日志匹配总数、已加载数及快照末尾提示。移除通用隐藏规则，390像素可见性断言由失败转通过。
2. 原生dialog的Tab末尾可离开弹窗进入浏览器工具栏。Modal补充Tab/Shift+Tab首尾循环，跳过不可见和禁用控件，保留Escape取消与焦点归还。角色全键盘提交及资源键盘二次确认通过。
3. 集合编辑器发出`invalidated`却未声明事件。真实403触发Vue警告；补充声明后警告消失，父级刷新与禁重复保存仍通过。

| 场景 | 已验证内容 |
| --- | --- |
| 登录/退出 | 窄屏、错误口令反馈、保留账号后重试、Tab与Enter提交、键盘退出 |
| 部门 | 窄屏键盘新增、类型/父级焦点顺序、重名错误及请求号、修改后重试、改名、Escape取消删除、确认删除后重载/回读 |
| 人员 | 跨候选页选择并整体保存、重新进入保留全量角色、空集合二次确认/取消、调动及停用的取消/保存、重载后部门/角色/状态及原职位 |
| 级别/范围变化 | 部门经理不见全局维护菜单，同级只读；编辑期间移出目标人员，旧草稿提交403，禁再次提交，原角色保持，关闭后目标从列表消失 |
| 角色 | 空权限新建、全键盘焦点循环/归还、改名及启停二次确认、取消不写、重载持久化、权限非空/空集合保存 |
| 即时授权变化 | 员工授予总览权限后可登录；停用角色及撤权后旧令牌总览403，重载无总览内容，角色关联保留；重新启用恢复准入 |
| 资源 | 类型和启停的取消/确认/重载、API映射非空/空集合、API停用/启用、父级保存及移回根级、搜索保留祖先、窄屏键盘二次确认 |
| 日志 | 条数变化阻止旧快照续批、续批保持total、详情Enter打开/Escape关闭及焦点归还、空结果总数、时间倒置错误 |
| 失败/保护 | 初读失败禁保存、完整重试、候选失败保留已选、写失败保留草稿后重试；最后维护者409保留草稿/请求号，原授权未变 |

本轮完成上述structured范围，不声称覆盖所有数据组合、浏览器品牌、屏幕阅读器或真实手机。ddd同项运行、生产部署与课程接入尚未执行；万人初始化及后端178项回归沿阶段5历史证据，本轮未重跑这些无源码变更的检查。

## 3. 复现

使用独立MySQL实例中的全新`rbac_core`库，按[后端说明](../backend/README.md)配置连接及启动。脚本只生成INSERT事务，不清库或覆盖记录，固定主键冲突拒绝重复导入。完整场景会停用`ui-user-05`，重跑整套应准备全新实例。不得应用到真实业务库。

仓库根目录执行（Python 3.12及`bcrypt`包）：

```powershell
# 在本地环境设置UI_FIXTURE_PASSWORD，不放入命令参数或报告。
python scripts/ui_acceptance_fixture.py --output .local/ui-fixture.sql

# 先创建私有实例的全新rbac_core库；连接参数放.local/ui-mysql.cnf。
$OutputEncoding = [System.Text.UTF8Encoding]::new()
Get-Content -Raw -Encoding UTF8 deploy/rbac-core-schema.sql,deploy/rbac-core-runtime.sql,.local/ui-fixture.sql |
  mysql --defaults-extra-file=.local/ui-mysql.cnf --default-character-set=utf8mb4 --database=rbac_core
```

后端设`APP_BACKEND_STRATEGY=structured`并启动；前端`API_TARGET`指向该后端。进入`frontend`：

```powershell
npm ci
npx playwright install chromium
# 另一个终端先以npm run dev -- --port 5183启动前端。
$env:UI_BASE_URL='http://127.0.0.1:5183'
$env:UI_ACCEPTANCE='1'
# UI_FIXTURE_PASSWORD与生成夹具时一致。
npm run test:e2e
npm test
npm run build
```

未设置`UI_ACCEPTANCE=1`时用例跳过，不能视为通过。[测试代码](../frontend/e2e/structured.spec.mjs)不保存storageState、令牌或网络trace；结果与截图在`.local/ui-browser-results`。断言与视口配置参考[Playwright断言](https://playwright.dev/docs/test-assertions)和[视口配置](https://playwright.dev/docs/emulation)。
