/**
 * DetailPanel — floating glassmorphism panel for Mode 2 class detail view.
 *
 * Shows class metrics (PageRank, Betweenness, Closeness, Fan-in, Fan-out),
 * conditional badges (Hotspot, Bridge), and blast-radius counts with an
 * affected-ratio percentage when a class node is clicked inside the
 * boundary container.
 *
 * Dismiss via close button, click-away, or Esc key.
 * Uses safe DOM APIs exclusively (no innerHTML).
 */

// ── Public types ──────────────────────────────────────────────────────

export interface NodeData {
  id: string;
  shortLabel: string;
  domain: string;
  metadata?: {
    metrics?: {
      pageRank?: number;
      betweenness?: number;
      closeness?: number;
    };
    issues?: {
      hotspot?: boolean;
      bridge?: boolean;
      cycle?: boolean;
    };
  };
}

export interface BlastRadius {
  p0: number;
  p1: number;
  p2: number;
  totalNodes: number;
}

// ── Implementation ────────────────────────────────────────────────────

export class DetailPanel {
  private readonly container: HTMLElement;
  private panel: HTMLElement | null = null;
  private boundClickAway: ((e: MouseEvent) => void) | null = null;
  private boundEsc: ((e: KeyboardEvent) => void) | null = null;

  constructor(container: HTMLElement) {
    this.container = container;
  }

  // ── Public API ─────────────────────────────────────────────────────

  show(node: NodeData, blastRadius: BlastRadius, fanIn: number, fanOut: number): void {
    // Remove existing panel if already visible
    this.removePanel();

    const panel = document.createElement('div');
    panel.className = 'detail-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', `Detail panel for ${node.shortLabel}`);

    // Close button
    const closeBtn = document.createElement('button');
    closeBtn.className = 'detail-panel-close';
    closeBtn.textContent = '\u00D7'; // multiplication sign as close glyph
    closeBtn.setAttribute('aria-label', 'Close detail panel');
    closeBtn.addEventListener('click', () => this.hide());
    panel.appendChild(closeBtn);

    // Class name
    const nameEl = document.createElement('div');
    nameEl.className = 'detail-panel-name';
    nameEl.textContent = node.shortLabel;
    panel.appendChild(nameEl);

    // Package path (everything before the last dot)
    const packagePath = node.id.includes('.')
      ? node.id.substring(0, node.id.lastIndexOf('.'))
      : '';
    const packageEl = document.createElement('div');
    packageEl.className = 'detail-panel-package';
    packageEl.textContent = packagePath;
    panel.appendChild(packageEl);

    // Metrics grid (2-column)
    const metricsGrid = document.createElement('div');
    metricsGrid.className = 'detail-metrics-grid';

    const metrics = node.metadata?.metrics;
    this.appendMetric(metricsGrid, 'PageRank', metrics?.pageRank);
    this.appendMetric(metricsGrid, 'Betweenness', metrics?.betweenness);
    this.appendMetric(metricsGrid, 'Closeness', metrics?.closeness);
    this.appendMetric(metricsGrid, 'Fan-in', fanIn);
    this.appendMetric(metricsGrid, 'Fan-out', fanOut);

    panel.appendChild(metricsGrid);

    // Badges (conditional)
    const issues = node.metadata?.issues;
    const hasHotspot = issues?.hotspot === true;
    const hasBridge = issues?.bridge === true;

    if (hasHotspot || hasBridge) {
      const badgesEl = document.createElement('div');
      badgesEl.className = 'detail-badges';

      if (hasHotspot) {
        const badge = document.createElement('span');
        badge.className = 'detail-badge hotspot';
        badge.textContent = 'HOTSPOT';
        badgesEl.appendChild(badge);
      }

      if (hasBridge) {
        const badge = document.createElement('span');
        badge.className = 'detail-badge bridge';
        badge.textContent = 'BRIDGE';
        badgesEl.appendChild(badge);
      }

      panel.appendChild(badgesEl);
    }

    // Blast radius section
    const blastSection = document.createElement('div');
    blastSection.className = 'detail-blast-radius';

    const blastTitle = document.createElement('div');
    blastTitle.className = 'detail-blast-title';
    blastTitle.textContent = 'BLAST RADIUS';
    blastSection.appendChild(blastTitle);

    this.appendBlastRow(blastSection, 'P0 (direct)', blastRadius.p0);
    this.appendBlastRow(blastSection, 'P1 (transitive)', blastRadius.p1);
    this.appendBlastRow(blastSection, 'P2 (deep)', blastRadius.p2);

    // Affected ratio
    const affected = blastRadius.p0 + blastRadius.p1 + blastRadius.p2;
    const total = blastRadius.totalNodes;
    const ratio = total > 0
      ? ((affected / total) * 100).toFixed(1) + '%'
      : '0.0%';

    this.appendBlastRow(blastSection, 'Affected', ratio);

    panel.appendChild(blastSection);

    // Attach to container
    this.container.appendChild(panel);
    this.panel = panel;

    // Install dismiss listeners
    this.installDismissListeners();
  }

  hide(): void {
    this.removePanel();
  }

  isVisible(): boolean {
    return this.panel !== null;
  }

  destroy(): void {
    this.removePanel();
  }

  // ── Private helpers ────────────────────────────────────────────────

  private removePanel(): void {
    if (this.panel) {
      this.panel.remove();
      this.panel = null;
    }
    this.uninstallDismissListeners();
  }

  private installDismissListeners(): void {
    // Click-away: listen on container, hide if target is outside panel
    this.boundClickAway = (e: MouseEvent) => {
      if (!this.panel) return;
      const target = e.target as Node;
      if (!this.panel.contains(target)) {
        this.hide();
      }
    };
    this.container.addEventListener('click', this.boundClickAway);

    // Esc key
    this.boundEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && this.isVisible()) {
        e.stopPropagation();
        this.hide();
      }
    };
    document.addEventListener('keydown', this.boundEsc, true);
  }

  private uninstallDismissListeners(): void {
    if (this.boundClickAway) {
      this.container.removeEventListener('click', this.boundClickAway);
      this.boundClickAway = null;
    }
    if (this.boundEsc) {
      document.removeEventListener('keydown', this.boundEsc, true);
      this.boundEsc = null;
    }
  }

  /**
   * Append a 2-column metric row (label + value) to the grid.
   * Numbers are formatted to 3 decimal places; undefined shows "---".
   */
  private appendMetric(grid: HTMLElement, label: string, value: number | undefined): void {
    const labelEl = document.createElement('div');
    labelEl.className = 'detail-metric-label';
    labelEl.textContent = label;

    const valueEl = document.createElement('div');
    valueEl.className = 'detail-metric-value';
    valueEl.textContent = value !== undefined && value !== null
      ? value.toFixed(3)
      : '---';

    grid.appendChild(labelEl);
    grid.appendChild(valueEl);
  }

  /**
   * Append a blast-radius row (label on left, count on right).
   */
  private appendBlastRow(parent: HTMLElement, label: string, count: number | string): void {
    const row = document.createElement('div');
    row.className = 'detail-blast-row';

    const labelEl = document.createElement('span');
    labelEl.className = 'blast-label';
    labelEl.textContent = label;

    const countEl = document.createElement('span');
    countEl.className = 'blast-count';
    countEl.textContent = String(count);

    row.appendChild(labelEl);
    row.appendChild(countEl);
    parent.appendChild(row);
  }
}
