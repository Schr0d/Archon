# Archon

> **如果我改了这个文件，什么会坏？**

[English](README.md) | [中文文档](README-zh.md)

Archon 扫描你的代码库依赖关系，告诉你任何改动的爆炸半径。在动那个 1600 行、50 个 `@Autowired` 的 Spring 服务之前，你会知道确切有哪 23 个文件依赖它。

一个问题，一个工具。确定性分析，本地运行，不上传云端。

---

## 它能做什么

```bash
# "谁依赖了这个服务？"
java -jar archon.jar analyze . --target com.example.UserService

影响范围: 23 个依赖，横跨 5 个域
  com.auth.handler.TokenValidator → UserService  (跨域)
  com.order.processor.CheckoutFlow → UserService  (跨域)
  ...

# "我改了 3 个文件，影响多大？"
java -jar archon.jar diff

变更: 3 个文件
爆炸半径: 14 个文件，2 条跨域依赖，0 个循环
  UserService → TokenValidator    [跨域]
  UserService → CheckoutFlow      [跨域]

# "给我完整的依赖图。"
java -jar archon.jar analyze .

分析完成: 127 个节点，312 条边，0 个循环，5 个域
热点:    com.example.UserService (入度: 18, 风险: 高)
```

---

## 为什么造这个东西

我在重构一个 Spring 服务。1600 行，50 个 `@Autowired` 字段。我改了一个 private 字段。所有测试全绿。上线。六个服务挂了。

IDE 的依赖分析抓不到 Spring DI。测试只覆盖正常路径。Code review 追不到 18 层传递依赖。"谁依赖了谁"这个知识只在运行时才存在，而那时已经晚了。

Archon 让这个知识在你改代码之前就可见。它用真正的解析器，不是 AI 猜测：ArchUnit 扫 Java 字节码（能抓 `@Autowired`、`@Resource`、构造器注入），dependency-cruiser 解析 JS/TS，import 解析器处理 Python。

---

## 快速开始

### 前提条件

- **Java 17**（OpenJDK 17+，如 [Eclipse Adoptium Temurin](https://adoptium.net/)）

### 安装

从 [releases](https://github.com/Schr0d/Archon/releases) 下载最新 JAR，或从源码构建：

```bash
./gradlew shadowJar
# 输出: archon-cli/build/libs/archon-1.0.0.jar
```

### 运行

```bash
# 完整依赖分析
java -jar archon.jar analyze /path/to/project

# 影响分析 — 改某个模块会波及什么？
java -jar archon.jar analyze /path/to/project --target com.example.Service

# 机器可读 JSON（给 AI 工具用）
java -jar archon.jar analyze . --format agent

# 未提交更改的影响范围
java -jar archon.jar diff

# 两个分支之间对比
java -jar archon.jar diff main feature-branch
```

---

## 给 AI Agent 用

Archon 设计为在 AI 编码工具的"计划-执行-审查"循环中被调用。

**用 Claude Code？** 输入 `/archon diff` 或 `/archon analyze` 即可获得即时影响分析。详见 [skill.md](skill.md)。

### 计划阶段：AI 获取架构上下文

```bash
java -jar archon.jar analyze . --format agent
```

返回结构化 JSON：依赖图、节点指标（PageRank、中介中心性、影响分数）、域分组、循环、热点、盲点。AI 利用这些信息避开高风险区域，尊重域边界。

### 执行阶段：AI 在约束下修改代码

AI agent 使用依赖上下文来限定修改范围，避开热点和跨域违规。

### 审查阶段：AI 提交前验证

```bash
java -jar archon.jar diff main HEAD
```

```
新增依赖: 2
  com.auth.service → com.payment.client [跨域]
  com.payment.dao → com.database.pool   [同域]

违规: 1
  max_cross_domain 超限（当前: 4，限制: 3）
门: 阻塞
```

AI 回滚违规并重新规划。

---

## 支持的语言

| 语言 | 解析器 | 能抓到什么 |
|------|--------|-----------|
| Java | ArchUnit 字节码 | 直接引用、Spring DI（`@Autowired`、`@Resource`、构造器注入） |
| JavaScript / TypeScript | dependency-cruiser | ES6 import、CommonJS、Vue SFC、路径别名 |
| Python | 导入解析器 | `import`、`from...import`、相对导入 |

插件基于 SPI 接口。添加新语言不需要修改核心代码。

---

## 架构

```
archon-core/     语言无关的图模型、分析引擎、SPI
archon-java/     Java 解析器插件（含 Spring DI 后处理器）
archon-js/       JavaScript/TypeScript 解析器插件
archon-python/   Python 导入解析器插件
archon-cli/      带有 shadow JAR 打包的 CLI
```

每个插件返回结构化声明。核心构建图、运行分析（中心性、域、循环、热点），输出人类可读或机器可读格式。

---

## 构建

```bash
./gradlew test        # 428 个测试
./gradlew shadowJar   # 输出 archon-cli/build/libs/archon-1.0.1.0.jar
```

---

## 路线图

- [x] v0.1 — CLI + 基础分析
- [x] v0.2 — 基于 diff 的分析
- [x] v0.3 — 多语言 SPI
- [x] v0.4 — 安全加固 + Vue 支持
- [x] v0.5 — 可视化（Web UI）
- [x] v0.6 — 跨语言边检测
- [x] v0.7 — JS/TS 重写 + Spring DI 检测 + 命令精简
- [x] v1.0 — 稳定版：多语言分析、AI agent 集成、压缩 agent 格式

---

## 贡献

参见 [TODOS.md](TODOS.md) 了解待办事项和贡献机会。

## 许可证

MIT

## 致谢

使用 [ArchUnit](https://archunit.org/) 进行 Java 字节码分析（Apache 2.0）。

## 链接

- [skill.md](skill.md) — AI agent 集成指南
- [CHANGELOG.md](CHANGELOG.md) — 版本历史
- [TODOS.md](TODOS.md) — 待办事项
