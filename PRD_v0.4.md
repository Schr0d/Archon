# Domain Analyzer & Dependency Guard PRD（v0.4 - Systems Engineering Edition）

> **Implementation status (2026-04-03):** v0.4.0.0 complete. Multi-language SPI + Java + JavaScript/TypeScript plugins + Vue support. Security hardening (namespace collision detection, file size validation, prefix validation). Diff-based change impact analysis. Tested on RuoYi monolith (1043 classes) + geditor-ui projects (375 + 489 JS/TS files). Sections 3-7 of this PRD remain the reference architecture; implementation status tracked in CLAUDE.md roadmap.

---

## 1. 一句话定义（Reframed）

> **一个用于“理解系统结构、评估变更影响、控制复杂度”的工程决策工具**

---

## 2. 核心设计原则（First Principles）

本系统基于系统工程第一性原理构建：

---

### 2.1 系统 = 元素 + 关系 + 目的

* 元素：类 / 模块 / 服务
* 关系：依赖 / 调用 / 数据流
* 目的：业务能力（auth / doc / tool）

👉 本工具重点关注：**关系建模**

---

### 2.2 结构决定行为

* 依赖关系 → 决定改动成本
* 模块边界 → 决定耦合程度
* 调用路径 → 决定风险传播

👉 本工具通过结构分析预测行为风险

---

### 2.3 杠杆点（Leverage Points）

* 少数节点（如 SysUser）对系统影响巨大

👉 本工具识别高影响节点（Hotspot）

---

### 2.4 变化传播（Propagation）

* 改动会沿依赖关系扩散

👉 本工具提供 Impact Analysis

---

### 2.5 边界控制复杂度

* 清晰边界 → 稳定系统
* 模糊边界 → 耦合失控

👉 本工具提供 Domain Analyzer

---

### 2.6 可观测性（Observability）

* 不可观测 → 不可控制

👉 本工具通过图与报告提升系统可观测性

---

## 3. 产品目标（Updated Goals）

---

### 核心目标

让用户具备三种能力：

1. **看清系统结构（Structure Awareness）**
2. **预测变更影响（Impact Prediction）**
3. **控制系统复杂度（Complexity Control）**

---

### 延伸目标

支持以下工程行为：

* 架构评审（Architecture Review）
* 变更评估（ECP）
* agent 输出校验（AI Guard）

---

## 4. 用户故事（强化版）

---

### US-01：结构认知

> 我需要快速理解系统结构，否则无法做任何决策

**成功标准**

* 用户 5 分钟内识别核心模块与耦合关系

---

### US-02：结构风险识别

> 我需要知道系统哪里“已经坏了”

**输出**

```text
❌ Cycle: system ↔ security
⚠️ Hotspot: SysUser
⚠️ Fake Domain: system
```

**成功标准**

* 用户能定位 ≥80% 关键结构问题

---

### US-03：变更影响评估（核心）

> 我需要在修改前知道风险，否则不敢动

**成功标准**

* 用户能在改动前明确：

  * 影响范围
  * 风险等级
  * 涉及关键路径

---

### US-04：ECP（工程变更评估）

> 我需要像系统工程一样评审改动

**成功标准**

* 工具输出可直接用于技术评审

---

### US-05：结构可视化（必须）

> 我必须通过图理解系统

**成功标准**

* 用户通过图完成结构理解（而非代码）

---

## 5. 核心能力（Reframed）

---

### 5.1 Structure Modeling（结构建模）

* 构建 dependency graph
* 支持多层级（class / module / domain）

---

### 5.2 Structural Risk Detection（结构风险识别）

包括：

* 循环依赖
* 高耦合节点
* 边界违规
* “垃圾桶模块”

---

### 5.3 Impact Propagation Engine（影响传播引擎）⭐核心

* 输入：变更点
* 输出：

  * 传播路径
  * 影响范围
  * 风险等级

---

### 5.4 Domain Boundary Analyzer（边界分析）

* 自动识别 domain
* 检测边界穿透
* 输出 domain 置信度

---

### 5.5 Constraint System（约束系统）

* 定义架构规则
* 自动检测违规

---

### 5.6 Visualization（结构可视化）

提供：

* 模块图
* 热点图
* 影响传播图

---

## 6. 风险评估模型（Risk Model）

---

### 6.1 评估维度

* 耦合度（Coupling）
* 跨域程度（Cross-domain）
* 关键路径（Critical Path，如 auth）
* 影响范围（Propagation Size）

---

### 6.2 输出等级

```text
LOW / MEDIUM / HIGH / VERY HIGH
```

---

### 6.3 原则

* 可解释（Explainable）
* 可调（Configurable）
* 不依赖黑盒模型

---

## 7. ECP（Engineering Change Proposal）

---

### 输入

```yaml
change:
  type: move_class
  target: SysUser
```

---

### 输出

```text
ECP-001

Impact:
- Affected: 12 classes / 3 modules
- Breaking: auth flow

Risk: HIGH

Benefits:
+ improved boundary clarity

Recommendation:
⚠️ staged migration
```

---

## 8. 成功判据（Success Criteria）

---

### 8.1 功能层

* 能构建系统结构图
* 能检测结构风险
* 能输出 impact 分析
* 能生成 ECP

---

### 8.2 用户行为（关键）

用户行为发生变化：

* 改代码前先跑分析
* 使用工具辅助决策
* 对系统结构有稳定认知

---

### 8.3 工程价值

* 减少“误改核心逻辑”风险
* 提高重构信心
* 降低结构劣化速度

---

## 9. 路线图（Roadmap）

---

### v0.1

* CLI + 基础分析

---

### v0.2

* Impact + Rule

---

### v0.3

* 可视化

---

### v0.4

* ECP + 决策支持

---

## 10. 最终定位（Final Positioning）

> **系统结构的“观测 + 评估 + 控制”层**

---
