# Design System — Archon

## Product Context
- **What this is:** Multi-language dependency analysis tool for impact analysis, hotspot detection, and blind spot reporting during AI-assisted refactoring
- **Who it's for:** Software engineers, architects, technical leads, AI workflows
- **Space/industry:** Developer tools, code analysis, visualization
- **Project type:** CLI tool with web visualization interface

## Aesthetic Direction
- **Direction:** Industrial/Utilitarian
- **Decoration level:** Minimal — function-first, no ambient glow, no starfield, no pulse animations. Trust through clarity.
- **Mood:** Trustworthy, precise, no-nonsense. The tool a senior architect reaches for when they need to understand what breaks.
- **Reference aesthetic:** Grafana, Datadog, engineering dashboards — dense, readable, purposeful

## Typography
- **Display/Headers:** `Geist` — Geometric, technical, modern. Less rounded than Inter, more engineered.
- **Body/UI:** `Geist` — Consistent with display
- **Data/Metrics:** `Geist Mono` — Technical, tabular numbers, code-friendly
- **Code:** `Geist Mono` — Monospace for all technical content
- **Loading:** Google Fonts — `https://fonts.googleapis.com/css2?family=Geist+Mono:wght@400;500;600&family=Geist:wght@400;500;600&display=swap`
- **Fallback:** `-apple-system`, `Segoe UI`, sans-serif

**Scale (rem → px):**
- Display: 1rem (16px)
- Body: 0.8125rem (13px)
- Small: 0.75rem (12px)
- Tiny: 0.6875rem (11px)

## Color

### Dark Theme (Default)
```css
--bg-app: #0a0a0b              /* Near-black, not pure black */
--bg-surface: #141416          /* Cards, panels, UML packages */
--bg-surface-hover: #1c1c1f    /* Interactive states */
--bg-boundary: rgba(20, 20, 22, 0.95)  /* Domain boundary container */

--border-subtle: rgba(255,255,255,0.06)
--border-default: rgba(255,255,255,0.10)
--border-strong: rgba(255,255,255,0.15)
--border-boundary: rgba(56, 189, 248, 0.20)  /* UML domain boundary */

--text-primary: #fafafa
--text-secondary: #a1a1aa
--text-tertiary: #71717a
--text-muted: #52525b

--accent: #38bdf8              /* Sky blue — trustworthy, technical */
--accent-soft: rgba(56, 189, 248, 0.12)
--accent-dim: rgba(56, 189, 248, 0.50)

--status-warning: #fbbf24
--status-warning-soft: rgba(251,191,36,0.15)

--status-error: #f87171
--status-error-soft: rgba(248,113,113,0.15)

--status-success: #4ade80
--status-success-soft: rgba(74,222,128,0.15)

--hotspot: #f87171             /* Hotspot indicator */
--hotspot-soft: rgba(248,113,113,0.12)
```

### Light Theme
```css
--bg-app: #fafafa
--bg-surface: #ffffff
--bg-surface-hover: #f5f5f5
--bg-boundary: rgba(255, 255, 255, 0.95)

--border-subtle: rgba(0,0,0,0.06)
--border-default: rgba(0,0,0,0.10)
--border-strong: rgba(0,0,0,0.14)
--border-boundary: rgba(14, 165, 233, 0.25)

--text-primary: #1a1a1a
--text-secondary: #737373
--text-tertiary: #a3a3a3
--text-muted: #d4d4d4

--accent: #0ea5e9
--accent-soft: rgba(14,165,233,0.10)
--accent-dim: rgba(14,165,233,0.50)

--status-warning: #d97706
--status-error: #dc2626
--status-success: #16a34a
```

### Semantic Color Usage
- **Accent (sky blue):** Internal dependency arrows, selected states, interactive highlights, P0 impact
- **Warning (amber):** Cross-boundary arrows, bridge nodes, P1/P2 impact levels, coupling signals
- **Hotspot (red):** Hotspot badges, high-coupling warnings
- **Success (green):** Socket "in" ports, healthy metrics, low-risk indicators

## Spacing
- **Base unit:** 4px
- **Density:** Compact — maximize information per viewport
- **Scale:** 4px, 8px, 12px, 16px, 20px, 24px

## Layout
- **Approach:** Grid-disciplined with canvas-based graph areas
- **App container:** `display: grid; grid-template-columns: 240px 1fr;`
- **Sidebar:** 240px fixed width
- **Border radius:** `sm: 4px`, `md: 6px`, `lg: 8px`

## Motion
- **Approach:** Minimal-functional
- **No ambient animations.** No glow pulses. No starfield.
- **Transitions only:** 150ms ease-out for state changes (hover, select, expand/collapse)
- **Reduced motion:** `prefers-reduced-motion: reduce` → instant transitions only

## Two-Mode Progressive Disclosure

There are two modes, not three. Mode 2 handles both browsing and detail via hover/click interactions.

### Mode 1: Domain Overview (UML Canvas)
- **Purpose:** High-level architectural overview showing all domains and inter-domain dependencies
- **Layout:** Canvas with UML package notation, dot grid background
- **Domain nodes:** Rounded rect boxes with name, class count, health dot. Explicit widths, positioned on a 3-row grid.
- **Edges:** L-shape SVG polylines between domain boxes, accent dashed
- **Background:** Dot grid (`radial-gradient` 1px dots, 24px spacing)
- **Interaction:** Click domain to drill into Mode 2
- **Arrow routing:** Start/end on box edge centers. L-shape: `|dx| >= |dy|` → horizontal first, else vertical first.

### Mode 2: Module Drill-down (Boundary Container)
- **Purpose:** All classes within a domain, their relationships, cross-boundary dependencies, and impact analysis. This is the primary working view.
- **Layout:** Boundary container (large rounded-rect) with positioned class nodes and SVG arrow overlay
- **Class nodes:** Draggable within boundary. Positioned absolutely, constrained to boundary interior.
- **Boundary sockets:** Box cards on domain edges representing cross-boundary connections. Draggable along their edge only (left/right edge = vertical, top/bottom edge = horizontal).
- **Arrow routing:** L-shape polylines. Start/end on node edge centers, determined by relative position of source/target.

#### Arrow Color Semantics
- **Internal deps:** Accent sky blue (`var(--accent)`) — solid, opacity 0.6. Primary content inside a boundary.
- **Cross-boundary deps:** Warning amber (`var(--status-warning)`) — dashed `4 3`, opacity 0.45. Coupling signal to external domains.
- **Arrowheads:** `<marker>` definitions, filled to match line color.

#### Interaction: Hover (Impact Analysis)
- **Trigger:** Hover over any class node
- **Effect:** Impact propagation overlay on the boundary canvas
  - **Selected node:** Accent border + glow + accent-soft background
  - **P0 direct:** Accent border, 1px ring
  - **P1 transitive:** Warning amber border, 1px ring
  - **P2 deep:** Warning amber dashed border, opacity 0.7
  - **Not impacted:** Dimmed to 15% opacity
- **SVG lines:** Colored by propagation level (P0=accent thick, P1=warning medium, P2=warning dashed thin, dimmed=invisible)
- **Legend:** "Blast radius" box in top-right corner
- **Dismiss:** Mouse leave, or Esc

#### Interaction: Click (Floating Detail Panel)
- **Trigger:** Click on any class node
- **Effect:** Floating panel appears in bottom-right corner of boundary
- **Panel style:** Glassmorphism (`backdrop-filter: blur(16px)`, semi-transparent bg), 240px wide
- **Panel content:**
  - Class name + package path
  - Metrics grid: PageRank, Betweenness, Closeness, Fan-in, Fan-out
  - Badges: Hotspot, Bridge
  - Blast radius: P0/P1/P2 counts, affected ratio
- **Dismiss:** Close button, click-away, or Esc

#### Socket System
- **Purpose:** Represent cross-boundary connections as box cards on domain edges
- **Layout:** Positioned on boundary edges. Right/bottom = outgoing, left/top = incoming.
- **Components:** Label (class/domain name) + port indicator (`.out` amber square, `.in` green square)
- **Drag:** Constrained to their edge axis only
- **SVG connection:** L-shape polyline from internal class node edge center to socket position

## Visual Effects
- **None.** No ambient glow. No starfield. No pulse animations.
- Dot grid background for spatial reference only
- Subtle edge glow zones on boundary edges where cross-boundary arrows exit (gradient fade)

## Components

### UML Package Node (Mode 1)
- Rounded rect: `border-radius: 4px`, `padding: 8px 14px`
- Border: `1px solid var(--border-default)`
- Fill: `var(--bg-surface)`
- Content: health dot + name + class count, `display: flex; gap: 8px`
- Font: `12px Geist Mono`
- Explicit widths per package (varies by name length)
- Selected: accent border + shadow

### Boundary Container (Mode 2)
- Large rounded rect: `border-radius: 6px`
- Border: `1px solid var(--border-boundary)`
- Background: `var(--bg-boundary)` + dot grid
- Label: Domain name positioned at top edge, accent color
- Fixed dimensions to align SVG overlay with positioned DOM elements
- SVG overlay: `position: absolute; inset: 0; z-index: 4; pointer-events: none;`

### Class Node (Mode 2)
- Small rounded rect: `border-radius: 4px`, `padding: 6px 10px`
- Background: `var(--bg-surface)`
- Border: `1px solid var(--border-default)`
- Font: `11px Geist Mono`
- Cursor: `grab` (draggable)
- Hotspot variant: warning border tint, `var(--hotspot-soft)` background

### Boundary Socket (Mode 2)
- Box card on boundary edge: `border-radius: 3px`, `padding: 2px 6px`
- Background: `var(--bg-surface)`, border: `1px solid var(--border-default)`
- Font: `9px Geist Mono` label + 5px port square
- Port `.out`: amber background, Port `.in`: green background
- Draggable along edge axis only

### Arrow (SVG, L-shape polyline)
- **Internal:** `stroke: var(--accent); stroke-width: 1; fill: none; opacity: 0.6;`
- **Cross-boundary:** `stroke: var(--status-warning); stroke-width: 1; stroke-dasharray: 4 3; fill: none; opacity: 0.45;`
- **Arrowheads:** `<marker>` definitions, filled to match line color
- **Routing:** Single-bend L-shape. Exit/enter edge determined by relative source/target position. Point = center of that edge.

### Floating Detail Panel (Mode 2, on click)
- Position: `absolute; bottom: 12px; right: 12px;`
- Width: 240px, z-index: 30
- Glassmorphism: `backdrop-filter: blur(16px); background: rgba(20, 20, 22, 0.92);`
- Close button: top-right corner
- Content: class name, package path, metrics grid (2-col), badges, blast radius section

### Impact Overlay States (Mode 2, on hover)
- `.impact-selected`: accent border + 2px ring + 24px glow + accent-soft bg
- `.impact-p0`: accent border + 1px ring
- `.impact-p1`: warning border + 1px ring
- `.impact-p2`: warning dashed border, opacity 0.7
- `.impact-dimmed`: opacity 0.15

### Sidebar Tree
- Domain nodes: 13px Geist, expandable with chevron
- Class nodes: 11px Geist Mono, indented
- Health indicator: 6px colored dot
- Active state: accent background tint

### Status Bar
- 32px height, fixed at bottom
- Glassmorphism: `backdrop-filter: blur(20px)`
- Metrics: Geist Mono numbers

## Connection Point Logic
Nodes are positioned with absolute left/top coordinates. Each node has a known width and height (~28px for class nodes, ~34px for packages).

1. **Determine exit/enter edge:** Based on relative position of source and target. Target is to the right → source exits right edge, target enters left edge.
2. **Calculate edge center:** The midpoint of the relevant edge (e.g., right edge center = left + width, top + height/2).
3. **Route L-shape:** `|dx| >= |dy|` → horizontal segment first (`M x1,cy L x2,cy L x2,y2`). Otherwise vertical first (`M cx,y1 L cx,y2 L x2,y2`).

Box dimension estimation: `width ≈ chars × 7px + 22px padding/border`, `height ≈ 28px` (class) or `34px` (package).

## Drag Behavior
- **Class nodes:** Free drag within boundary, constrained to interior with 4px margin.
- **Boundary sockets:** Constrained to their edge axis. Left/right edge sockets move vertically only. Top/bottom edge sockets move horizontally only.
- **On drag start:** Convert `right`/`bottom` CSS to `left`/`top` for consistent coordinate math.
- **Z-index:** Dragged element gets z-index 20 during drag.

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-04-09 | Initial design system created via /design-consultation | Modern SaaS aesthetic with green accent |
| 2026-04-12 | Complete redesign to Industrial/Utilitarian | Information density > visual delight. Previous direction prioritized aesthetics over the core use case (understanding dependency impact at a glance) |
| 2026-04-12 | Sky blue accent (#38bdf8) replaces green (#4ade80) | Blue reads as trustworthy/technical. Green implies "healthy/good" which is misleading for a tool that surfaces problems. |
| 2026-04-12 | Removed all ambient animations | Glow, starfield, and pulse effects add visual noise that competes with the actual data. A dependency graph IS the visual interest. |
| 2026-04-12 | UML canvas with package notation (Mode 1) | Engineers already read UML. Reusing a familiar notation reduces learning curve. Rounded rects are more space-efficient than card grids for showing relationships. |
| 2026-04-12 | SVG arrow overlay on positioned DOM (Mode 2) | SVG gives precise arrow routing. DOM gives crisp text rendering. Mixing both gives the best of each. Fixed container dimensions keep SVG and DOM coordinates aligned. |
| 2026-04-12 | Two-mode model replaces three-mode | Mode 3 was artificial separation. Hover shows impact, click shows detail, both within Mode 2. Reduces navigation depth and keeps context. |
| 2026-04-12 | L-shape polyline routing | Bezier curves felt too soft/unengineered. Orthogonal L-shapes are deterministic, easy to compute, and match the industrial aesthetic. Single bend per line. |
| 2026-04-12 | Internal = accent, cross-boundary = amber | Inside a boundary, internal deps are the primary content (accent). Cross-boundary deps are coupling signals (amber warning). Previous gray was invisible in dark mode and semantically wrong (it IS dependency data). |
| 2026-04-12 | Boundary sockets as box cards on edges | Sockets make cross-boundary connections explicit and tangible. Box cards with port indicators (`.out` amber, `.in` green) are more readable than bare line endpoints. |
| 2026-04-12 | Impact analysis as hover state, not separate mode | Impact overlay on hover keeps spatial context. User sees blast radius without losing their place. Click-away or Esc dismisses. No mode switching required. |
| 2026-04-12 | Floating detail panel on click | Bottom-right glassmorphism panel shows class metrics + blast radius without replacing the boundary view. Stays in context. |
| 2026-04-12 | Draggable nodes, edge-constrained sockets | Users need to rearrange nodes for readability. Sockets constrained to boundary edges because that is their semantic meaning (edge connection points). |
| 2026-04-12 | Mode 1 arrows connect to box edge centers | Previous arrows floated in space with guessed coordinates. Now calculated from actual box positions + dimensions. Documented edge center coordinates for each domain. |
