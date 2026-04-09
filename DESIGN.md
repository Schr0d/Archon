# Design System — Archon

## Product Context
- **What this is:** Multi-language dependency analysis tool for impact analysis, hotspot detection, and blind spot reporting during AI-assisted refactoring
- **Who it's for:** Software engineers, architects, technical leads, AI workflows
- **Space/industry:** Developer tools, code analysis, visualization
- **Project type:** CLI tool with web visualization interface

## Aesthetic Direction
- **Direction:** Modern SaaS with subtle tech-magic
- **Decoration level:** Intentional — ambient glow, starfield, pulse animations (alive but not distracting)
- **Mood:** Professional yet delightful — a tool senior engineers trust and enjoy using
- **Reference aesthetic:** Linear, Vercel, Raycast — clean, modern, with delightful micro-interactions

## Typography
- **Display/Headers:** `Inter` — Clean, readable, modern sans-serif
- **Body/UI:** `Inter` — Consistent with display
- **Data/Metrics:** `Geist Mono` — Technical, tabular numbers, code-friendly
- **Code:** `Geist Mono` — Monospace for all technical content
- **Fallback:** `SF Mono`, `Monaco`, `Courier New`
- **Loading:** Google Fonts — `https://fonts.googleapis.com/css2?family=Geist+Mono:wght@400;500;600&family=Inter:wght@400;500;600&display=swap`

**Scale (rem → px):**
- Display: 1rem (16px)
- Body: 0.8125rem (13px)
- Small: 0.75rem (12px)
- Tiny: 0.6875rem (11px)

## Color
**Approach:** Restrained with punchy accents

### Dark Theme (Default)
```css
--bg-app: #0c0c0e              /* Deep background, not pure black */
--bg-surface: #151518          /* Cards, panels */
--bg-surface-hover: #1c1c1f     /* Interactive states */
--bg-surface-active: #222225    /* Active/pressed */

--border-subtle: rgba(255,255,255,0.06)    /* Barely visible */
--border-default: rgba(255,255,255,0.10)  /* Standard borders */
--border-strong: rgba(255,255,255,0.15)   /* Emphasized borders */

--text-primary: #fafafa        /* Main content */
--text-secondary: #a1a1aa      /* Labels, descriptions */
--text-tertiary: #71717a       /* Hints, metadata */
--text-muted: #52525b          /* Disabled, decorative */

--accent-primary: #4ade80      /* Success green - brighter than typical */
--accent-primary-soft: rgba(74,222,128,0.15)   /* Background tint */
--accent-primary-glow: rgba(74,222,128,0.35)   /* Glow effects */

--status-warning: #fbbf24      /* Warning amber */
--status-warning-soft: rgba(251,191,36,0.15)

--status-error: #f87171        /* Error red (softer) */
--status-error-soft: rgba(248,113,113,0.15)
```

### Light Theme
```css
--bg-app: #fafafa              /* Off-white */
--bg-surface: #ffffff          /* Pure white cards */
--bg-surface-hover: #f5f5f5
--bg-surface-active: #ebebeb

--border-subtle: rgba(0,0,0,0.06)
--border-default: rgba(0,0,0,0.10)
--border-strong: rgba(0,0,0,0.14)

--text-primary: #1a1a1a
--text-secondary: #737373
--text-tertiary: #a3a3a3
--text-muted: #d4d4d4

--accent-primary: #16a34a      /* Slightly darker for light mode */
--accent-primary-soft: rgba(22,163,74,0.10)
--accent-primary-glow: rgba(22,163,74,0.20)

--status-warning: #d97706
--status-error: #dc2626
```

### Semantic Color Usage
- **Healthy:** `--accent-primary` — Green for good state
- **Warning:** `--status-warning` — Amber for caution
- **Error:** `--status-error` — Red for problems

## Spacing
- **Base unit:** 4px
- **Density:** Comfortable — room for information to breathe
- **Scale:** 4px, 8px, 12px, 16px, 20px, 24px

## Layout
- **Approach:** Grid-disciplined with creative interactions
- **App container:** `display: grid; grid-template-columns: 240px 1fr;`
- **Sidebar:** 240px fixed width (expanded from 200px for comfort)
- **Max content width:** None — full viewport usage
- **Border radius scale:**
  - `--radius-sm: 6px`
  - `--radius-md: 8px`
  - `--radius-lg: 12px`
  - `--radius-xl: 16px`

## Motion
- **Approach:** Intentional — animations that feel responsive and delightful
- **Easing (bouncy):** `cubic-bezier(0.34, 1.56, 0.64, 1)` — For playful interactions (hover, icon animations)
- **Easing (smooth):** `cubic-bezier(0.16, 1, 0.3, 1)` — For standard transitions
- **Duration:**
  - Micro: 150ms — Hover states, button interactions
  - Short: 200ms — Theme transitions
  - Medium: 300ms — Page loads, fade-ins
  - Long: 600ms — Complex animations

**Transition tokens:**
```css
--ease-out: cubic-bezier(0.16, 1, 0.3, 1);
--transition: 150ms var(--ease-out);
```

### Key Animations
- **Fade in:** `fadeIn` — 0.6s ease-out with subtle translateY
- **Pulse glow:** `pulse-glow` — 2s ease-in-out infinite for selected nodes
- **Ambient move:** `ambient-move` — 20s ease-in-out infinite for background
- **Logo pulse:** `logo-pulse` — 3s ease-in-out infinite
- **Tooltip in:** `tooltip-in` — 0.3s with scale and translateY

## Visual Effects

### Ambient Glow
```css
/* Moving radial gradient background */
background: radial-gradient(
  ellipse at 30% 20%,
  var(--accent-primary-glow) 0%,
  transparent 50%
);
animation: ambient-move 20s ease-in-out infinite;
```

### Starfield
```css
/* Multiple radial gradients creating subtle star points */
background-image:
  radial-gradient(1px 1px at 20% 30%, rgba(255,255,255,0.1) 50%, transparent 100%),
  radial-gradient(1px 1px at 60% 70%, rgba(255,255,255,0.08) 50%, transparent 100%),
  radial-gradient(1px 1px at 50% 50%, rgba(74,222,128,0.1) 50%, transparent 100%),
  radial-gradient(1px 1px at 80% 10%, rgba(255,255,255,0.05) 50%, transparent 100%),
  radial-gradient(1px 1px at 90% 60%, rgba(255,255,255,0.07) 50%, transparent 100%),
  radial-gradient(1.5px 1.5px at 40% 80%, rgba(74,222,128,0.15) 50%, transparent 100%),
  radial-gradient(1px 1px at 10% 90%, rgba(255,255,255,0.06) 50%, transparent 100%);
animation: twinkle 8s ease-in-out infinite;
```

### Canvas Grid
```css
/* Subtle dot grid for spatial reference */
background-image:
  radial-gradient(circle, var(--border-subtle) 1px, transparent 1px);
background-size: 24px 24px;
opacity: 0.5;
```

### Scrollbar
```css
/* Minimal custom scrollbar */
::-webkit-scrollbar { width: 8px; height: 8px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover { background: var(--border-strong); }
```

### Glassmorphism
- Applied to header and status bar
- `backdrop-filter: blur(20px)`
- Subtle but functional depth

### Glow Effects
- Selected nodes: `--glow-strong: 0 0 60px rgba(74,222,128,0.2)`
- Hover states: `--glow-subtle: 0 0 30px rgba(74,222,128,0.1)`
- Logo: Continuous pulse animation

## Components

### Node (Graph)
- Card-style with icon + content + optional badge
- Border radius: `12px`
- Padding: `12px 16px`
- Shadow: `0 2px 8px rgba(0,0,0,0.1)`
- **Top accent:** Gradient line on hover (`::before` pseudo-element with `linear-gradient(90deg, transparent, var(--accent-primary), transparent)`)
- Hover: `transform: translateY(-2px) scale(1.01)` + icon scale 1.1 + rotate 5deg
- Selected: Pulsing glow effect + accent border
- Warning/Error states: Colored borders + glows

### Edge Connectors
- Width: `2px`
- Height: `32px`
- Color: `var(--text-tertiary)` with `opacity: 0.6`
- Arrow: 5px borders (transparent left/right, solid top)

### Sidebar Tree
- Domain nodes: 14px font, expandable with chevron
- Class nodes: 12px monospace, indented
- Health indicator: 6px colored dot
- Active state: Soft background tint

### Status Bar
- 36px height, fixed at bottom
- Glassmorphism: `backdrop-filter: blur(20px)`
- Metrics: Monospace numbers
- Badges: Soft background + colored text

### Tooltip
- 220px width
- Glassmorphism background
- Animation: Scale + fade in
- Info rows with labels and monospace values

## Two-Level View Hierarchy

### Domain Level (Overview)
- **Purpose:** High-level architectural overview
- **Content:** Domain nodes, inter-domain dependencies, domain-level metrics
- **Cognitive limit:** 7-9 domains visible at once
- **Layout:** Left-to-right flow, grouped by layer

### Class Level (Detail)
- **Purpose:** Fine-grained dependency inspection
- **Content:** Individual classes, inter-class dependencies, external deps
- **Navigation:** Click domain to expand, breadcrumb back
- **Layout:** Compact cluster view

## Interactions

### Hover States
- Scale: `1.01-1.02`
- Y-translate: `-1px to -2px`
- Border: `--border-strong`
- Shadow: Enhanced
- Icon: Rotate 5deg, scale 1.1

### Click/Selection
- Immediate border color change to accent
- Pulsing glow animation starts
- Tooltip appears with animation

### Theme Switch
- Smooth transition: `0.3s ease`
- All color variables update via CSS custom properties
- No flash, seamless experience

## Accessibility
- WCAG AA contrast ratios (all text combinations tested)
- Focus indicators on all interactive elements
- Keyboard navigation support
- Reduced motion query support (can add `@media (prefers-reduced-motion)`)

## Export Formats
For AI workflow integration:
- **SVG:** Vector export with styling preserved
- **PNG:** Raster export with current theme
- **JSON:** Structured data for programmatic analysis
- **HTML:** Self-contained with inline styles

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-04-09 | Initial design system created via /design-consultation | Based on modern SaaS aesthetics (Linear, Vercel, Raycast) with added delight for long work sessions |
| 2026-04-09 | Dark theme uses #0c0c0e, not pure black | Pure black is too harsh for extended use; slight gray is more comfortable |
| 2026-04-09 | Accent green is #4ade80, not #22c55e | Brighter, more vibrant; feels more "active" and modern |
| 2026-04-09 | Ambient glow + starfield effects | Adds "wow factor" without distracting from data; makes tool feel alive |
| 2026-04-09 | Geist Mono for all technical content | Excellent tabular numbers, modern feel, better than JetBrains Mono for UI |
| 2026-04-09 | Bouncy easing (cubic-bezier(0.34,1.56,0.64,1)) | Makes interactions feel playful and responsive; encourages exploration |
| 2026-04-09 | Design system updated from archon-viz-design-preview.html sketch | Aligned DESIGN.md with implemented HTML preview; added smooth easing, canvas grid, scrollbar styles, edge connectors |
