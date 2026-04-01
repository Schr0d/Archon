# Domain Analyzer & Dependency Guard PRD（v0.5 - Trust & ECP Edition）

---

## 1. 一句话定义（升级）

> **一个提供“结构可观测性 + 变更影响评估 + 架构约束执行”的工程决策系统（带可信度声明）**

---

## 2. 核心设计原则（新增约束）

---

### 2.1 Deterministic First（确定性优先）

* 依赖关系 / 调用图 = 客观事实
* 必须由静态分析生成
* 不允许 LLM 干预底层结构计算

---

### 2.2 Declare Uncertainty（声明不确定性）⭐关键

> **工具必须有能力说：“我不知道”**

---

引入概念：

```text
Blind Spot（盲区）
```

来源：

* 反射
* 动态代理
* EventBus
* Agent Prompt Flow

---

输出必须标记：

```text
⚠️ Dynamic Invocation Detected
⚠️ Analysis Confidence: LOW
```

---

👉 原则：

> **宁可保守，不可误导**

---

### 2.3 Human-in-the-loop（人类最终决策）

* 工具提供结构与评估
* 人类做最终决策

---

### 2.4 Taste → Constraint（工程品味转规则）⭐核心

* 所有“感觉不优雅”的判断
* 最终必须沉淀为规则

---

## 3. 系统能力升级

---

## 3.1 Structure Modeling（结构建模）

输出：

* Dependency Graph
* Domain Graph

---

新增字段：

```text
Node:
- domain
- tags (auth / core / edge)
- confidence

Edge:
- type (static / dynamic)
- confidence
```

---

## 3.2 Blind Spot Detection（盲区检测）⭐新增

---

检测：

* 反射调用
* 动态加载
* 事件驱动
* agent flow

---

输出：

```text
⚠️ Blind Spot:
- EventBus detected
- Reflection usage

Confidence: LOW
```

---

可视化：

* 灰色 / 虚线节点

---

## 3.3 Cycle Detection（升级为阻断机制）⭐

---

规则：

```text
Cycle = ERROR（不是 warning）
```

---

行为：

* 检测到环：

  * ❌ 拒绝生成 ECP
  * ❌ 拒绝进行 Impact 评估
  * 强制提示：

```text
❌ Circular Dependency Detected
Action Required: Break the cycle first
```

---

## 3.4 Impact Propagation Engine（升级）

---

支持：

* BFS 传播
* N-degree 限制（默认 3）

---

新增：

### 传播权重

```text
weight(node) =
  domain_weight +
  coupling_weight +
  critical_path_weight
```

---

## 3.5 Diff-Based Analysis（变更驱动）⭐新增

---

输入：

```text
Change Set（Git Diff）
```

---

输出：

```text
Impact Radius (N=2):
- affected modules
- affected services
```

---

👉 原则：

> **只分析“这次改动会影响什么”**

---

## 3.6 Domain Analyzer（增强）

---

新增：

### Domain Confidence

```text
system:
confidence: LOW
reason: mixed concerns
```

---

### Boundary Heatmap

* 跨域调用频率

---

## 3.7 Risk Model（量化版）⭐关键

---

### 维度

| 维度           | 规则             |
| ------------ | -------------- |
| Coupling     | in-degree      |
| Cross-domain | domain 数量      |
| Depth        | 调用层级           |
| Critical     | 是否在关键路径        |
| Cycle        | 是否在环中          |
| Confidence   | 是否有 blind spot |

---

---

### v0.1 量化规则（你可以直接用）

```text
Coupling:
- >10 → HIGH
- 5~10 → MEDIUM

Cross-domain:
- ≥3 → HIGH

Depth:
- ≥3 → HIGH

Critical Path:
- auth / payment → HIGH

Cycle:
- YES → VERY HIGH (BLOCK)

Confidence:
- LOW → +1 risk level
```

---

### 输出

```text
Risk:
LOW / MEDIUM / HIGH / VERY HIGH / BLOCKED
```

---

## 3.8 ECP Engine（升级）

---

新增字段：

```text
Confidence: HIGH / MEDIUM / LOW
```

---

示例：

```text
ECP-001

Impact:
- Affected: 12 classes
- Modules: auth / doc

Risk: HIGH
Confidence: MEDIUM

Blind Spots:
- EventBus detected

Recommendation:
⚠️ manual review required
```

---

## 3.9 LLM Integration（严格限制）⭐

---

LLM 仅允许：

### 1. 表达层

* 生成 ECP 文本
* 解释风险

---

### 2. 建议层

* 提供重构建议

---

禁止：

* 生成 dependency graph ❌
* 计算 impact ❌
* 修改规则 ❌

---

👉 原则：

> **LLM = 顾问，不是裁判**

---

## 4. 规则系统（工程品味落地）

---

### 示例规则（v0.1）

```yaml
rules:
  - no_cycle: true
  - max_cross_domain: 2
  - max_call_depth: 3
  - forbid_core_entity_leakage:
      - SysUser
```

---

### 输出：

```text
❌ call depth exceeded: 4
❌ SysUser leaked to 5 domains
```

---

---

## 5. 用户故事（更新）

---

### US-06：面对不确定性

> 我希望工具告诉我哪里“不确定”，而不是假装全知

**成功标准**

* 所有动态依赖被标记
* 提供 confidence 指标

---

---

### US-07：变更驱动分析

> 我只关心“这次改动会发生什么”

**成功标准**

* 用户能在 1 分钟内看到 Impact Radius

---

---

### US-08：架构约束自动执行

> 我希望工具替我执行我的工程品味

**成功标准**

* PR 中违规自动被检测
* 支持 CI 集成

---

---

## 6. 成功判据（升级）

---

### 功能

* 支持 Blind Spot 标记
* 支持 Diff 分析
* Cycle 阻断生效

---

### 用户行为

* 用户信任工具输出（关键）
* 用户开始依赖 Risk + Confidence 做决策

---

---

## 7. 产品哲学（最终版）

---

> **我们不替人做决定，我们让系统变得可理解、可评估、可控制**

---

> **确定性的部分交给规则，不确定性的部分必须被显式标记，人类负责最终判断**

---
