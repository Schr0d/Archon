# Archon Viz Module Redesign — Design Specification

**Date:** 2025-04-08
**Status:** Draft
**Version:** 0.1.0

## Executive Summary

Complete redesign of the Archon visualization module to address critical UX issues in the current implementation (1800+ lines, unstable layout, poor usability). The redesign adopts a modular architecture with two complementary visualization themes and a split-screen synchronized interaction model.

**Current Problems:**
- Monolithic codebase (single 1800+ line file)
- Unstable automatic layout requiring constant manual adjustment
- Poor visual hierarchy and information density
- No clear mental model for navigation
- Theme locked to dark mode only

**Design Goals:**
- Modular architecture with clear separation of concerns
- Stable, predictable layouts using dagre.js
- Two-level view hierarchy (Domain → Class)
- Split-screen synchronized navigation
- Dual theme support (NOC Dark + Blueprint Light)

---

## 1. Architecture

### 1.1 Modular Structure

```
archon-viz/
├── core/
│   ├── GraphCore.ts          # Core graph data structures
│   ├── Node.ts               # Node model with metadata
│   └── Edge.ts               # Edge model with dependencies
├── model/
│   ├── AnalysisResult.ts     # Parsed Archon output model
│   ├── DomainNode.ts         # Domain-level node
│   └── ClassNode.ts          # Class-level node
├── builder/
│   ├── GraphBuilder.ts       # Constructs graph from analysis
│   └── NodeFactory.ts        # Creates typed nodes
├── layout/
│   ├── DagreLayoutEngine.ts  # dagre.js integration
│   └── LayeredLayout.ts      # Domain/Class layered layout
├── render/
│   ├── GraphRenderer.ts      # D3.js rendering orchestration
│   ├── NodeRenderer.ts       # Node visualization
│   ├── EdgeRenderer.ts       # Edge visualization
│   └── ThemeManager.ts       # Theme application
├── ui/
│   ├── SplitScreen.ts        # Left tree + Right graph layout
│   ├── DomainTreeView.ts     # Left sidebar domain tree
│   ├── GraphCanvas.ts        # Right visualization canvas
│   ├── StatusBar.ts          # Bottom status bar
│   └── SyncManager.ts        # Cross-view synchronization
└── styles/
    ├── noc-theme.css         # Dark monitoring theme
    └── blueprint-theme.css   # Light technical theme
```

### 1.2 Data Flow

```
Archon JSON Output
       ↓
AnalysisResult (parsed)
       ↓
GraphBuilder → constructs GraphCore
       ↓
DagreLayoutEngine → computes positions
       ↓
GraphRenderer → D3.js renders SVG
       ↓
User Interaction → SyncManager → updates both views
```

### 1.3 Interface Boundaries

- **Core:** Immutable graph structures, pure functions
- **Model:** Typed data transfer objects
- **Builder:** Converts analysis to graph (one-way)
- **Layout:** Positions nodes (idempotent)
- **Render:** Visualizes positioned graph (stateless)
- **UI:** Manages interaction state and sync

---

## 2. Two-Level View Hierarchy

### 2.1 Domain Level (Overview)

**Purpose:** High-level architectural overview

**Content:**
- Domains as top-level nodes
- Inter-domain dependencies
- Domain-level metrics (class count, hotspot count, cycle detection)
- Health status indicators

**Layout:**
- Horizontal hierarchy (left-to-right flow)
- Grouped by architectural layer
- Max 7-9 domains visible at once (cognitive limit)

### 2.2 Class Level (Detail)

**Purpose:** Fine-grained dependency inspection

**Content:**
- Individual class nodes within selected domain
- Inter-class dependencies
- External dependency indicators
- Hotspot and cycle warnings

**Layout:**
- Compact cluster view
- Intra-domain edges visible
- External dependencies summarized

### 2.3 Navigation Between Levels

- Click domain node → expand to class view
- Click outside → collapse back to domain view
- Breadcrumb navigation: `All Domains → <Domain Name>`
- Back/forward navigation history

---

## 3. Split-Screen Layout

### 3.1 Structure

```
┌─────────────────────────────────────────────────────┐
│                      Header                          │
│              Archon Dependency Analysis              │
├──────────────┬──────────────────────────────────────┤
│              │                                       │
│   Domain     │         Graph Canvas                  │
│    Tree      │    (Current level visualization)     │
│              │                                       │
│              │                                       │
│              │                                       │
├──────────────┴──────────────────────────────────────┤
│                   Status Bar                         │
│    Domains: 7 | Classes: 80 | Edges: 124 | Warn: 1  │
└─────────────────────────────────────────────────────┘
```

### 3.2 Left Sidebar — Domain Tree

**Purpose:** Navigation and overview

**Features:**
- Collapsible tree of all domains
- Domain names with class counts
- Health indicators (color-coded)
- Click to navigate to domain
- Current selection highlighted
- Always visible, independent of graph view

**Interaction:**
- Single click: View domain in graph canvas
- Double click: Expand/collapse subtree
- Hover: Show domain summary tooltip

### 3.3 Right Canvas — Graph Visualization

**Purpose:** Detailed dependency exploration

**Features:**
- Interactive D3.js force/dagre layout
- Zoom and pan
- Node drag (with spring return)
- Click for details
- Hover for tooltip

**Synchronized Highlighting:**
- Hover tree node → highlight graph node
- Hover graph node → highlight tree node
- Click tree → center graph on node
- Click graph → expand tree to node

---

## 4. Layout Strategy

### 4.1 dagre.js Integration

**Why dagre:**
- Stable, predictable layouts
- Hierarchical edge routing
- Minimal edge crossings
- TypeScript support

**Configuration:**
```typescript
const dagreConfig = {
  nodesep: 50,      // Horizontal spacing
  ranksep: 80,      // Vertical spacing
  edgesep: 20,      // Spacing between parallel edges
  rankdir: 'LR'     // Left-to-right direction
};
```

### 4.2 Readability Priorities

1. **Minimize edge crossings** — dagre handles automatically
2. **Align related nodes** — group by domain/layer
3. **Respect hierarchy** — dependency direction visible
4. **Avoid overlap** — fixed node sizes, calculated spacing

### 4.3 Performance Optimizations

- Layout computed once, cached
- Incremental updates for node selection changes
- Web Worker for large graphs (100+ nodes)
- Progressive rendering for initial load

---

## 5. Interaction Flow

### 5.1 Initial Load

1. Parse Archon JSON output
2. Build domain-level graph
3. Compute initial layout
4. Render domain view in canvas
5. Populate domain tree
6. Show summary in status bar

### 5.2 Domain Exploration

```
User Action: Click domain in tree
              ↓
SyncManager: Highlight domain node in graph
              ↓
GraphRenderer: Center and scale to domain
              ↓
StatusBar: Update metrics for selection
```

### 5.3 Drill Down to Classes

```
User Action: Double-click domain node
              ↓
GraphBuilder: Build class-level graph for domain
              ↓
DagreLayoutEngine: Compute class layout
              ↓
GraphRenderer: Render class view with transition
              ↓
Breadcrumb: Add domain to navigation history
```

### 5.4 Return to Overview

```
User Action: Click "All Domains" breadcrumb
              ↓
GraphRenderer: Fade out class view
              ↓
GraphRenderer: Fade in domain view
              ↓
Tree: Clear domain selection
```

### 5.5 External Dependencies

- Shown as dashed edges
- Labeled with external package name
- Clicking shows warning: "External dependency, cannot analyze"
- Counted in metrics but not traversable

---

## 6. Style System

### 6.1 Theme Architecture

Two complete, independent themes:

- **NOC Theme** — Dark monitoring room aesthetic
- **Blueprint Theme** — Light technical drawing aesthetic

Themes are CSS classes applied to `<body>`:
```html
<body class="theme-noc">     <!-- Dark theme -->
<body class="theme-blueprint"> <!-- Light theme -->
```

### 6.2 NOC Theme (Dark)

**Visual Reference:** Network Operations Center monitoring dashboard

**Color Palette:**
```css
--bg-primary: #000000
--bg-secondary: #0a0a0a
--bg-elevated: #0f0f0f
--border-subtle: #1a1a1a
--border-default: #222
--text-primary: #ffffff
--text-secondary: #888
--text-muted: #555
--accent-health: #00FF41      /* Precise green */
--accent-warning: #FF6B00     /* Warning orange */
--accent-error: #FF0000       /* Error red */
--accent-highlight: rgba(0, 255, 65, 0.1)
```

**Typography:**
- Headers: IBM Plex Mono 600/700
- Data: JetBrains Mono 400/500
- Monospace numbers for metrics

**Visual Characteristics:**
- Pure black background with subtle grid overlay
- 1px precise borders, minimal decoration
- High contrast, status-driven colors
- Pulsing animation for active nodes
- Optimized for long work sessions in dark environment

**Grid Background:**
```css
background-image:
  linear-gradient(rgba(0, 255, 65, 0.03) 1px, transparent 1px),
  linear-gradient(90deg, rgba(0, 255, 65, 0.03) 1px, transparent 1px);
background-size: 20px 20px;
```

### 6.3 Blueprint Theme (Light)

**Visual Reference:** Technical architectural blueprint

**Color Palette:**
```css
--bg-primary: linear-gradient(135deg, #f0f4f8 0%, #e8eef5 100%)
--bg-card: #ffffff
--border-default: #0066CC       /* Engineering blue */
--border-warning: #f59e0b
--text-primary: #1e293b
--text-secondary: #64748b
--text-muted: #94a3b8
--accent-primary: #0066CC
--accent-warning: #f59e0b
```

**Typography:**
- Headers: IBM Plex Mono 600
- Body: System sans-serif
- Labels uppercase with letter-spacing

**Visual Characteristics:**
- Gradient gray-blue background
- 2px precise borders, technical drawing style
- White cards with subtle drop shadows
- Blue accent color throughout
- Print-friendly, suitable for documentation

**Grid Background:**
```css
background-image:
  linear-gradient(rgba(0, 102, 204, 0.05) 1px, transparent 1px),
  linear-gradient(90deg, rgba(0, 102, 204, 0.05) 1px, transparent 1px);
background-size: 15px 15px;
```

### 6.4 Component Styling

**Nodes:**
- Rounded rectangles (4px radius in NOC, 2px in Blueprint)
- Border color indicates health status
- Background color indicates selection state
- Size varies by importance (domain > class)

**Edges:**
- 1.5px stroke width
- Color indicates edge type (internal, external, cycle)
- Arrow markers on destination
- Curved paths (bezier) for visual clarity

**Tooltips:**
- Same theme as parent
- Semi-transparent backdrop blur
- Compact information density
- Quick fade-in/out (150ms)

---

## 7. Information Density Principles

### 7.1 Cognitive Limits

**7 ± 2 Rule:** Humans can comfortably process 5-9 items at once

**Application:**
- Domain view: Max 7-9 domains visible without scroll
- Class view: Paginate at ~15 classes per view
- Use grouping for larger datasets

### 7.2 Progressive Disclosure

Show minimum information initially, reveal on demand:

- **Default:** Node name + health indicator
- **Hover:** Quick metrics (count, dependencies)
- **Click:** Full details panel
- **Double-click:** Expand/collapse

### 7.3 Visual Hierarchy

1. **Most important:** Health status (color)
2. **Second:** Node names (size, weight)
3. **Third:** Metrics (monospace, muted)
4. **Fourth:** Labels and annotations

---

## 8. Technical Implementation Notes

### 8.1 Technology Stack

- **D3.js v7** — Graph rendering and interaction
- **dagre-d3 v0.6** — Layout engine
- **TypeScript** — Type safety
- **Vite** — Build tooling (replaces webpack)

### 8.2 Performance Targets

- Initial render: < 2 seconds for 100 nodes
- Layout computation: < 500ms
- Interaction response: < 100ms (60fps)
- Memory: < 50MB for typical project

### 8.3 Accessibility

- Keyboard navigation (arrow keys, Enter, Escape)
- Screen reader announcements for selection changes
- High contrast mode support
- Focus indicators on all interactive elements
- ARIA labels for graph elements

### 8.4 Browser Support

- Chrome/Edge 90+ (primary)
- Firefox 88+ (secondary)
- Safari 14+ (best effort)

No IE11 support — modern APIs required (ResizeObserver, etc.)

---

## 9. Migration Strategy

### 9.1 Phase 1: Core Infrastructure

- Set up new modular structure
- Implement GraphCore and Node models
- Create GraphBuilder from Archon JSON
- Unit tests for core logic

### 9.2 Phase 2: Layout and Rendering

- Integrate dagre.js
- Implement basic D3 rendering
- Add zoom/pan interactions
- Test with sample data

### 9.3 Phase 3: Split Screen UI

- Build domain tree component
- Implement graph canvas
- Create sync manager
- Add status bar

### 9.4 Phase 4: Theming

- Implement NOC theme
- Implement Blueprint theme
- Add theme switcher
- Polish visual details

### 9.5 Phase 5: Integration

- Replace old ViewCommand
- Update CLI integration
- Migration guide for users
- Deprecate old implementation

---

## 10. Open Questions

[To be refined during implementation planning]

- Exact pagination strategy for large class sets
- Web Worker threshold (node count)
- Local storage preferences (theme, last view)
- Export functionality (SVG, PNG)

---

## Appendix: Visual Sketch

See `archon-viz-theme-sketch.html` for interactive visual comparison of both themes with the proposed split-screen layout.

**Features demonstrated in sketch:**
- Split layout (tree left, graph right)
- Both themes with complete styling
- Status bar with metrics
- Interactive hover states
- Domain and class level mockups
