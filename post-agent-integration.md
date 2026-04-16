# Archon: 给 AI Agent 加上架构约束层

最近在用 AI 做代码重构时发现一个实际问题：AI 很擅长写代码，但不太理解"边界"。

比如你想让 AI 重构一个模块，它可能会：
- 不小心改到了核心依赖
- 引入跨层调用
- 破坏了原有的模块边界

这些问题在 review 时才能发现，已经浪费了不少时间。

## 解决思路

Archon 做了一件事：把架构分析变成 AI 可读的上下文。

AI 在改动代码前先跑一次分析：

```bash
java -jar archon.jar analyze . --format agent > archon-context.json
```

得到这样的结构化信息：

```json
{
  "domains": [
    {"name": "core", "nodes": 45, "boundaries": ["com.myapp.core.*"]},
    {"name": "api", "nodes": 12, "boundaries": ["com.myapp.api.*"]}
  ],
  "hotspots": [
    {"node": "DependencyGraph", "inDegree": 18, "risk": "HIGH"}
  ]
}
```

AI 就知道：
- 哪些是高风险模块（尽量别动）
- 哪些是域边界（跨域要小心）
- 哪些是盲点（解析不到的动态调用）

改完代码后再跑一次 diff：

```bash
java -jar archon.jar diff main HEAD
```

如果违反了架构规则，AI 就知道要回滚重做。

## 实际效果

在我们自己的项目中用起来：

1. **减少回滚** — AI 提交的代码 80%+ 一次通过架构审查
2. **更安全的重构** — 高风险模块被显式标识，AI 会避开或先问人
3. **可追溯** — 每次 diff 都有记录，能看出架构是怎么演化的

## 谁适合用

- 用 AI 做大规模重构的团队
- 代码库比较老、边界不清晰的项目
- 想把 AI 代码审查自动化的

## 当前状态

Archon 支持 Java / JS / TS / Python，是开源的。刚加了中文文档和 AI 集成指南。

如果你也在用 AI 写代码，可能值得试试。GitHub: https://github.com/Schr0d/Archon

（不是 AI coding assistant，是给 AI 加的一层"护栏"）
