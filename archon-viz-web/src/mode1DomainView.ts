/**
 * mode1DomainView.ts -- Mode 1: Domain Overview (UML Package Canvas)
 *
 * Renders all domains as UML package boxes on a 3-row grid with
 * SVG L-shape polylines for inter-domain dependencies.
 *
 * Uses arrowRouter.ts for arrow routing and DOM+SVG architecture
 * (positioned DOM elements for boxes, SVG overlay for arrows).
 */

import { routeArrow, Rect } from './arrowRouter';

// ── Public types ──────────────────────────────────────────────────────

export interface DomainInfo {
  id: string;
  name: string;
  nodeCount: number;
  health: 'healthy' | 'warning' | 'error';
}

export interface InterDomainEdge {
  source: string; // domain id
  target: string; // domain id
}

// ── Internal types ────────────────────────────────────────────────────

interface BoxPosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

// ── Constants ─────────────────────────────────────────────────────────

/** Package box height matches DESIGN.md (34px with 8px padding top/bottom). */
const BOX_HEIGHT = 34;

/** SVG marker ID for accent-colored arrowheads. */
const MARKER_ACCENT = 'arrow-accent';

/** SVG marker ID for default-colored arrowheads. */
const MARKER_DEFAULT = 'arrow-default';

// ── Implementation ────────────────────────────────────────────────────

export class Mode1DomainView {
  private container: HTMLElement;
  private domains: DomainInfo[];
  private edges: InterDomainEdge[];
  private onDomainClick: (domainId: string) => void;

  /** DOM elements created by render(). */
  private domainElements: Map<string, HTMLElement> = new Map();
  private svgOverlay: SVGSVGElement | null = null;
  private resizeObserver: ResizeObserver | null = null;

  constructor(
    container: HTMLElement,
    domains: DomainInfo[],
    edges: InterDomainEdge[],
    onDomainClick: (domainId: string) => void,
  ) {
    this.container = container;
    this.domains = domains;
    this.edges = edges;
    this.onDomainClick = onDomainClick;
  }

  // ── Public API ────────────────────────────────────────────────────

  /** Render the domain overview into the container. Safe to call multiple times. */
  render(): void {
    const w = this.container.clientWidth;
    const h = this.container.clientHeight;

    // Defer if container not yet laid out
    if (w === 0 || h === 0) {
      requestAnimationFrame(() => this.render());
      return;
    }

    this.clear();
    this.container.style.position = 'relative';

    // Compute grid layout
    const positions = this.computeLayout(w, h);

    // Create SVG overlay first (lower z-index, behind packages)
    this.svgOverlay = this.createSvgOverlay(w, h);
    this.renderArrows(this.svgOverlay, positions);
    this.container.appendChild(this.svgOverlay);

    // Create UML package boxes
    for (const domain of this.domains) {
      const pos = positions.get(domain.id);
      if (!pos) continue;

      const el = this.createPackageElement(domain, pos);
      this.domainElements.set(domain.id, el);
      this.container.appendChild(el);
    }

    // Observe container resizes (only create once)
    if (!this.resizeObserver) {
      this.resizeObserver = new ResizeObserver(() => this.render());
      this.resizeObserver.observe(this.container);
    }
  }

  /** Tear down: remove all DOM elements and observers. */
  destroy(): void {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    this.clear();
  }

  // ── Layout ────────────────────────────────────────────────────────

  /**
   * Position domains on a 3-row grid.
   *
   * cols = ceil(domains.length / 3)
   * Each domain is centered within its grid cell.
   */
  private computeLayout(containerW: number, containerH: number): Map<string, BoxPosition> {
    const positions = new Map<string, BoxPosition>();
    if (this.domains.length === 0) return positions;

    const cols = Math.ceil(this.domains.length / 3);
    const colWidth = containerW / cols;
    const rowHeight = containerH / 3;

    for (let i = 0; i < this.domains.length; i++) {
      const domain = this.domains[i];
      const col = i % cols;
      const row = Math.floor(i / cols);
      const boxWidth = this.estimateBoxWidth(domain.name);

      const x = col * colWidth + (colWidth - boxWidth) / 2;
      const y = row * rowHeight + (rowHeight - BOX_HEIGHT) / 2;

      positions.set(domain.id, { x, y, width: boxWidth, height: BOX_HEIGHT });
    }

    return positions;
  }

  /** Box width estimate: max(80, name.length * 7 + 60) per spec. */
  private estimateBoxWidth(name: string): number {
    return Math.max(80, name.length * 7 + 60);
  }

  // ── UML Package DOM ───────────────────────────────────────────────

  /**
   * Create a single .uml-package DOM element.
   * Uses only createElement/textContent -- no innerHTML.
   */
  private createPackageElement(domain: DomainInfo, pos: BoxPosition): HTMLElement {
    const el = document.createElement('div');
    el.className = 'uml-package';
    el.setAttribute('role', 'button');
    el.setAttribute('tabindex', '0');
    el.setAttribute('aria-label', `${domain.name}, ${domain.nodeCount} classes, ${domain.health}`);
    el.dataset.domainId = domain.id;

    // Position
    el.style.left = `${pos.x}px`;
    el.style.top = `${pos.y}px`;

    // Health dot
    const healthDot = document.createElement('span');
    healthDot.className = `health-dot ${domain.health}`;
    healthDot.setAttribute('aria-hidden', 'true');

    // Package name
    const nameSpan = document.createElement('span');
    nameSpan.className = 'package-name';
    nameSpan.textContent = domain.name;

    // Class count
    const countSpan = document.createElement('span');
    countSpan.className = 'class-count';
    countSpan.textContent = String(domain.nodeCount);

    el.appendChild(healthDot);
    el.appendChild(nameSpan);
    el.appendChild(countSpan);

    // Click handler
    const handler = () => this.onDomainClick(domain.id);
    el.addEventListener('click', handler);
    el.addEventListener('keydown', (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handler();
      }
    });

    return el;
  }

  // ── SVG Arrows ────────────────────────────────────────────────────

  /** Create the SVG element that will hold all arrow polylines. */
  private createSvgOverlay(width: number, height: number): SVGSVGElement {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.classList.add('svg-overlay');
    svg.setAttribute('width', String(width));
    svg.setAttribute('height', String(height));
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);

    // Arrowhead marker definitions
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');

    // Accent marker (sky blue, for domain-level arrows)
    defs.appendChild(this.createMarker(MARKER_ACCENT, 'var(--accent)'));
    // Default marker (for future use / fallback)
    defs.appendChild(this.createMarker(MARKER_DEFAULT, 'var(--text-tertiary)'));

    svg.appendChild(defs);
    return svg;
  }

  /** Create a single <marker> element for SVG arrowheads. */
  private createMarker(id: string, fill: string): SVGMarkerElement {
    const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', id);
    marker.setAttribute('viewBox', '0 0 10 6');
    marker.setAttribute('refX', '10');
    marker.setAttribute('refY', '3');
    marker.setAttribute('markerWidth', '8');
    marker.setAttribute('markerHeight', '6');
    marker.setAttribute('orient', 'auto-start-reverse');

    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', 'M 0 0 L 10 3 L 0 6 z');
    path.setAttribute('fill', fill);

    marker.appendChild(path);
    return marker;
  }

  /** Render all inter-domain edges as L-shape polylines. */
  private renderArrows(svg: SVGSVGElement, positions: Map<string, BoxPosition>): void {
    for (const edge of this.edges) {
      const sourcePos = positions.get(edge.source);
      const targetPos = positions.get(edge.target);
      if (!sourcePos || !targetPos) continue;

      const sourceRect: Rect = {
        x: sourcePos.x,
        y: sourcePos.y,
        width: sourcePos.width,
        height: sourcePos.height,
      };
      const targetRect: Rect = {
        x: targetPos.x,
        y: targetPos.y,
        width: targetPos.width,
        height: targetPos.height,
      };

      const result = routeArrow(sourceRect, targetRect);
      if (!result.points) continue; // same-node guard

      const polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
      polyline.setAttribute('points', result.points);
      polyline.setAttribute('stroke', 'var(--accent)');
      polyline.setAttribute('stroke-width', '1');
      polyline.setAttribute('fill', 'none');
      polyline.setAttribute('opacity', '0.6');
      polyline.setAttribute('stroke-dasharray', '4 3');
      polyline.setAttribute('marker-end', `url(#${MARKER_ACCENT})`);

      svg.appendChild(polyline);
    }
  }

  // ── Cleanup ───────────────────────────────────────────────────────

  /** Remove all rendered elements from the container. */
  private clear(): void {
    // Remove SVG overlay
    if (this.svgOverlay && this.svgOverlay.parentNode) {
      this.svgOverlay.parentNode.removeChild(this.svgOverlay);
    }
    this.svgOverlay = null;

    // Remove domain elements
    for (const el of this.domainElements.values()) {
      if (el.parentNode) {
        el.parentNode.removeChild(el);
      }
    }
    this.domainElements.clear();
  }
}
