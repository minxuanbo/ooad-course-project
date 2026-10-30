# 小组合作代码贡献步骤

> 本仓库为**小组合作项目**

> **请严格按下面步骤操作**

---

## 一、开始之前（只做一次）

### 1️⃣ 确认你已经被加为 Collaborator

* 能看到这个仓库
* 能点开 Issues / Pull Requests

如果你：

* push 报错
* 提示 403

  👉 立刻联系仓库 owner

---

### 2️⃣ 把仓库克隆到本地

```bash
git clone https://github.com/minxuanbo/ooad-course-project.git
cd ooad-course-project
```

---

## 二、每次改代码前（非常重要）

### 3️⃣ 切换到 main 并更新代码

```bash
git checkout main
git pull
```

---

### 4️⃣ 新建一个分支（必须）

```bash
git checkout -b 分支名
```

示例：

```bash
git checkout -b feat/你的功能名
git checkout -b fix/eval-bug
git checkout -b exp/qwen-baseline
```
还有更多分支命名规范，AI查一下就行

⚠️ **不要在 main 分支直接改代码**

---

## 三、写代码 & 本地检查

### 5️⃣ 修改代码

* 正常写代码
* 改完后：

  * 至少能跑
  * 不要留下明显报错

---

### 6️⃣ 查看你改了什么
```bash
git status
git diff
```

如果你看到：

* 一堆不认识的文件
* 不知道为什么被改了

👉 **先停下来检查**

---

## 四、提交代码到你的分支

### 7️⃣ 添加改动

```bash
git add .
```

（如果你只想提交部分文件，可以单独 `git add 文件名`）

---

### 8️⃣ 提交 commit

```bash
git commit -m "改动部分"
```

示例：

```bash
git commit -m "[Fix] Fix map not rendering issue"
git commit -m "[Feat] Add AI-based map recommendation feature"
```
更多命名规范，AI一下就行

❌ 不要用以下模糊的描述（不方面溯源）：

```text
update
test
final
```

---

### 9️⃣ 推送到远程仓库（不是 main）

```bash
git push origin 你的分支名
```

例如：

```bash
git push origin fix/eval-bug
```

---

## 五、发起 Pull Request（PR）

### 🔟 在 GitHub 网页上操作

1. 打开仓库页面
2. 会看到 **“Compare & pull request”** 按钮
3. 点击它
4. 填写 PR 描述（不用写太多）

---

### PR 描述最简模板（照填）

```markdown
做了什么：
- 修复 / 新增 / 实验 …

是否影响结果：
- 是 / 否（不确定就写不确定）

是否本地跑过：
- 是 / 否
```

👉 **不会写也没关系，但一定要写点东西**

---

## 六、等待合并（不要自己 merge）

### 1️⃣ 提交 PR 后：

* 不要继续改 main
* 等 owner / 同学 review

---

### 2️⃣ 如果被要求修改

```bash
# 在同一个分支继续改
git add .
git commit -m "[Fix] 根据 PR 意见修改"
git push
```

👉 PR 会自动更新，不用新建 PR

---

## 七、PR 合并后你要做的事

### 1️⃣ 切回 main 并同步

```bash
git checkout main
git pull
```

### 2️⃣ 删除本地旧分支（可选但推荐）

```bash
git branch -d 你的分支名
```

---

## 八、⚠️ 严禁行为（请务必看）

❌ 直接 push main（一定不要这么做！有可能会覆盖掉其他同学修改后的代码）

❌ 不拉最新代码就开始改

❌ 一次 commit 改一堆不相关东西，描述还不写清楚

❌ PR 什么都不写

👉 **出现以上情况，后果是：代码冲突 + 结果不可信**

---

## 九、遇到问题怎么办？

如果你遇到：

* 冲突
* 不知道该怎么合并
* 不确定改法对不对

👉 **立刻在群里问 / GitHub 留言**

---

## 十、一句话记住就够了

> **main 不动 → 分支改 → 提交 → PR → 合并**

---
## 附录：一些好用的vscode插件（但本人也用的不是很熟）
1. GitLen
![GitLen](docs/images/GitLen.png)
2. Github PR
![Github_PR](docs/images/Github_PR.png)