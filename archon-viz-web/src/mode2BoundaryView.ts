/**
 * mode2BoundaryView.ts — Mode 2: Module Drill-down (Boundary Container)
 *
 * The primary working view. Renders a boundary container with:
 * - Absolutely-positioned DOM class nodes (draggable)
 * - Boundary sockets for cross-boundary connections (edge-constrained drag)
 * - SVG arrow overlay (internal + cross-boundary polylines)
 * - Hover impact analysis overlay (P0/P1/P2 propagation)
 * - Click detail panel (via DetailPanel)
 * - Back button + breadcrumb navigation
 *
 * Uses arrowRouter.ts for L-shape routing, impactAnalysis.ts for BFS
 * propagation, forceSimulation.ts for initial layout, and detailPanel.ts
 * for the floating metrics panel.
 *
 * All DOM construction uses createElement/textContent — no innerHTML.
 */

import { routeArrow, Rect } from './arrowRouter';
import { computeImpact, ImpactResult } from './impactAnalysis';
import { DetailPanel, NodeData, BlastRadius } from './detailPanel';
import { ForceSimulation } from './forceSimulation';

// ── Public types ──────────────────────────────────────────────────────

export interface ClassNodeData {
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
    };
  };
}

export interface SocketData {
  id: string;
  label: string;
  edge: 'top' | 'right' | 'bottom' | 'left';
  type: 'in' | 'out';
  connectedToClassId: string;
}

// ── Internal types ────────────────────────────────────────────────────

interface NodePosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

// ── Constants ─────────────────────────────────────────────────────────

/** Class node estimated height (matches CSS: 6px padding * 2 + 11px font + 2px border). */
const CLASS_NODE_HEIGHT = 28;

/** Minimum class node width for short labels. */
const CLASS_NODE_MIN_WIDTH = 60;

/** px per character for width estimation (11px monospace font). */
const PX_PER_CHAR = 7;

/** Padding/border overhead for class node width. */
const CLASS_NODE_PADDING = 22;

/** Margin from boundary edge when constraining drag. */
const DRAG_MARGIN = 4;

/** SVG marker ID for accent (sky blue) arrowheads. */
const MARKER_ACCENT = 'm2-arrow-accent';

/** SVG marker ID for amber (warning) arrowheads. */
const MARKER_AMBER = 'm2-arrow-amber';

/** Maximum force simulation iterations for initial layout. */
const MAX_SIM_ITERATIONS = 600;

// ── Implementation ────────────────────────────────────────────────────

export class Mode2BoundaryView {
  private container: HTMLElement;
  private domainId: string;
  private domainLabel: string;
  private classNodes: ClassNodeData[];
  private internalEdges: Array<{ source: string; target: string }>;
  private sockets: SocketData[];
  private socketEdges: Array<{ source: string; target: string }>;
  private onBack: () => void;

  /** DOM references for cleanup. */
  private boundaryEl: HTMLElement | null = null;
  private svgOverlay: SVGSVGElement | null = null;
  private classNodeElements: Map<string, HTMLElement> = new Map();
  private socketElements: Map<string, HTMLElement> = new Map();
  private nodePositions: Map<string, NodePosition> = new Map();
  private socketPositions: Map<string, NodePosition> = new Map();
  private detailPanel: DetailPanel | null = null;
  private impactLegend: HTMLElement | null = null;

  /** Active impact state. */
  private activeImpactNodeId: string | null = null;

  /** Bound event handlers for cleanup. */
  private boundKeyDown: ((e: KeyboardEvent) => void) | null = null;
  private boundBoundaryLeave: (() => void) | null = null;

  constructor(
    container: HTMLElement,
    domainId: string,
    domainLabel: string,
    classNodes: ClassNodeData[],
    internalEdges: Array<{ source: string; target: string }>,
    sockets: SocketData[],
    socketEdges: Array<{ source: string; target: string }>,
    onBack: () => void,
  ) {
    this.container = container;
    this.domainId = domainId;
    this.domainLabel = domainLabel;
    this.classNodes = classNodes;
    this.internalEdges = internalEdges;
    this.sockets = sockets;
    this.socketEdges = socketEdges;
    this.onBack = onBack;
  }

  // ── Public API ──────────────────────────────────────────────────────

  render(): void {
    const w = this.container.clientWidth;
    const h = this.container.clientHeight;

    // Defer if container not yet laid out
    if (w === 0 || h === 0) {
      requestAnimationFrame(() => this.render());
      return;
    }

    this.destroy();

    // 1. Boundary container
    this.boundaryEl = this.createBoundary(w, h);
    this.container.appendChild(this.boundaryEl);

    // 2. Back button + breadcrumb
    this.createNavigation(this.boundaryEl);

    // 3. Compute initial positions via force simulation
    this.computeInitialPositions(w, h);

    // 4. SVG overlay (created before nodes so it sits behind them)
    this.svgOverlay = this.createSvgOverlay(w, h);
    this.boundaryEl.appendChild(this.svgOverlay);

    // 5. Class nodes
    for (const nodeData of this.classNodes) {
      const el = this.createClassNodeElement(nodeData);
      this.classNodeElements.set(nodeData.id, el);
      this.boundaryEl.appendChild(el);
    }

    // 6. Sockets (createSocketElement already appends to boundary for measurement)
    for (const socketData of this.sockets) {
      const el = this.createSocketElement(socketData, w, h);
      this.socketElements.set(socketData.id, el);
    }

    // 7. Render all arrows
    this.renderAllArrows();

    // 8. Impact legend (hidden by default)
    this.impactLegend = this.createImpactLegend();
    this.boundaryEl.appendChild(this.impactLegend);

    // 9. Detail panel
    this.detailPanel = new DetailPanel(this.boundaryEl);

    // 10. Global listeners
    this.installGlobalListeners();
  }

  destroy(): void {
    // Remove global listeners
    this.uninstallGlobalListeners();

    // Clear impact state
    this.activeImpactNodeId = null;

    // Destroy detail panel
    if (this.detailPanel) {
      this.detailPanel.destroy();
      this.detailPanel = null;
    }

    // Remove all created elements
    for (const el of this.classNodeElements.values()) {
      if (el.parentNode) el.parentNode.removeChild(el);
    }
    this.classNodeElements.clear();

    for (const el of this.socketElements.values()) {
      if (el.parentNode) el.parentNode.removeChild(el);
    }
    this.socketElements.clear();

    if (this.svgOverlay && this.svgOverlay.parentNode) {
      this.svgOverlay.parentNode.removeChild(this.svgOverlay);
    }
    this.svgOverlay = null;

    if (this.impactLegend && this.impactLegend.parentNode) {
      this.impactLegend.parentNode.removeChild(this.impactLegend);
    }
    this.impactLegend = null;

    if (this.boundaryEl && this.boundaryEl.parentNode) {
      this.boundaryEl.parentNode.removeChild(this.boundaryEl);
    }
    this.boundaryEl = null;

    this.nodePositions.clear();
    this.socketPositions.clear();
  }

  // ── Boundary Container ──────────────────────────────────────────────

  private createBoundary(w: number, h: number): HTMLElement {
    const el = document.createElement('div');
    el.className = 'boundary-container';
    el.style.width = `${w}px`;
    el.style.height = `${h}px`;

    // Domain label at top edge
    const label = document.createElement('div');
    label.className = 'boundary-label';
    label.textContent = this.domainLabel;
    el.appendChild(label);

    return el;
  }

  // ── Navigation ──────────────────────────────────────────────────────

  private createNavigation(parent: HTMLElement): void {
    // Back button
    const backBtn = document.createElement('button');
    backBtn.className = 'mode-back-btn';
    backBtn.setAttribute('aria-label', 'Back to domain overview');
    backBtn.textContent = '\u2190 Back';
    backBtn.addEventListener('click', () => this.onBack());
    parent.appendChild(backBtn);

    // Breadcrumb
    const breadcrumb = document.createElement('div');
    breadcrumb.className = 'mode-breadcrumb';

    const domainSpan = document.createElement('span');
    domainSpan.className = 'domain-name';
    domainSpan.textContent = this.domainLabel;
    breadcrumb.appendChild(domainSpan);

    parent.appendChild(breadcrumb);
  }

  // ── Initial Layout via Force Simulation ──────────────────────────────

  private computeInitialPositions(containerW: number, containerH: number): void {
    // Compute estimated widths for each node
    const simNodes = this.classNodes.map((node, i) => {
      const w = this.estimateNodeWidth(node.shortLabel);
      return {
        id: node.id,
        x: 0,
        y: 0,
      };
    });

    const simEdges = this.internalEdges.map(e => ({
      source: e.source,
      target: e.target,
    }));

    if (simNodes.length === 0) return;

    // Seed initial positions: spread across available area
    const margin = 60;
    const availW = containerW - margin * 2;
    const availH = containerH - margin * 2;

    for (let i = 0; i < simNodes.length; i++) {
      if (simNodes.length <= 20) {
        // Circle layout for small graphs
        const angle = (2 * Math.PI * i) / simNodes.length;
        const rx = availW * 0.4;
        const ry = availH * 0.35;
        simNodes[i].x = margin + availW / 2 + Math.cos(angle) * rx;
        simNodes[i].y = margin + availH / 2 + Math.sin(angle) * ry;
      } else {
        // Random scatter for larger graphs
        simNodes[i].x = margin + Math.random() * availW;
        simNodes[i].y = margin + Math.random() * availH;
      }
    }

    // Run force simulation synchronously
    const sim = new ForceSimulation(simNodes, simEdges);
    let iterations = 0;
    while (sim.active && iterations < MAX_SIM_ITERATIONS) {
      sim.update();
      iterations++;
    }

    // Clamp all positions to boundary interior
    for (const node of sim.nodes) {
      const nodeW = this.estimateNodeWidth(
        this.classNodes.find(n => n.id === node.id)?.shortLabel || ''
      );
      const nodeH = CLASS_NODE_HEIGHT;

      node.x = Math.max(DRAG_MARGIN, Math.min(containerW - nodeW - DRAG_MARGIN, node.x));
      node.y = Math.max(DRAG_MARGIN, Math.min(containerH - nodeH - DRAG_MARGIN, node.y));

      this.nodePositions.set(node.id, {
        x: node.x,
        y: node.y,
        width: nodeW,
        height: nodeH,
      });
    }
  }

  // ── Class Node DOM ──────────────────────────────────────────────────

  private createClassNodeElement(nodeData: ClassNodeData): HTMLElement {
    const pos = this.nodePositions.get(nodeData.id);
    const x = pos?.x ?? 0;
    const y = pos?.y ?? 0;
    const w = pos?.width ?? this.estimateNodeWidth(nodeData.shortLabel);
    const h = pos?.height ?? CLASS_NODE_HEIGHT;

    const el = document.createElement('div');
    el.className = 'class-node';
    if (nodeData.metadata?.issues?.hotspot) {
      el.classList.add('hotspot');
    }
    el.dataset.nodeId = nodeData.id;
    el.setAttribute('role', 'button');
    el.setAttribute('tabindex', '0');
    el.setAttribute('aria-label', nodeData.shortLabel);
    el.style.left = `${x}px`;
    el.style.top = `${y}px`;
    el.textContent = nodeData.shortLabel;

    // Drag behavior
    this.makeDraggable(el, nodeData.id, w, h, true);

    // Hover → impact analysis
    el.addEventListener('mouseenter', () => {
      this.showImpactOverlay(nodeData.id);
    });

    // Click → detail panel
    el.addEventListener('click', (e: MouseEvent) => {
      e.stopPropagation();
      this.showDetailPanel(nodeData);
    });

    // Keyboard support
    el.addEventListener('keydown', (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        this.showDetailPanel(nodeData);
      }
    });

    return el;
  }

  // ── Socket DOM ──────────────────────────────────────────────────────

  private createSocketElement(socketData: SocketData, containerW: number, containerH: number): HTMLElement {
    const el = document.createElement('div');
    el.className = 'boundary-socket';
    el.dataset.socketId = socketData.id;

    // Port indicator
    const port = document.createElement('span');
    port.className = `socket-port ${socketData.type}`;
    port.setAttribute('aria-hidden', 'true');

    // Label
    const label = document.createElement('span');
    label.textContent = socketData.label;

    el.appendChild(port);
    el.appendChild(label);

    // Position on boundary edge (temporary attach to measure)
    el.style.visibility = 'hidden';
    this.boundaryEl.appendChild(el);
    this.positionSocket(el, socketData, containerW, containerH);
    el.style.visibility = '';

    // Edge-constrained drag
    this.makeSocketDraggable(el, socketData, containerW, containerH);

    return el;
  }

  private positionSocket(
    el: HTMLElement,
    socketData: SocketData,
    containerW: number,
    containerH: number,
  ): void {
    const elW = el.offsetWidth || 80;
    const elH = el.offsetHeight || 20;

    // Count sockets on same edge for distribution
    const sameEdgeSockets = this.sockets.filter(s => s.edge === socketData.edge);
    const index = sameEdgeSockets.indexOf(socketData);
    const total = sameEdgeSockets.length;

    // Distribute evenly along the edge
    let x = 0;
    let y = 0;

    switch (socketData.edge) {
      case 'left':
        x = 0;
        y = this.distributeAlongAxis(containerH, elH, index, total);
        break;
      case 'right':
        x = containerW - elW;
        y = this.distributeAlongAxis(containerH, elH, index, total);
        break;
      case 'top':
        x = this.distributeAlongAxis(containerW, elW, index, total);
        y = 0;
        break;
      case 'bottom':
        x = this.distributeAlongAxis(containerW, elW, index, total);
        y = containerH - elH;
        break;
    }

    el.style.left = `${x}px`;
    el.style.top = `${y}px`;

    this.socketPositions.set(socketData.id, { x, y, width: elW, height: elH });
  }

  /** Distribute N items evenly along an axis with margins. */
  private distributeAlongAxis(axisLength: number, itemSize: number, index: number, total: number): number {
    if (total <= 1) return (axisLength - itemSize) / 2;
    const margin = 40;
    const avail = axisLength - margin * 2 - itemSize;
    const step = avail / (total - 1);
    return margin + index * step;
  }

  // ── Drag Behavior ───────────────────────────────────────────────────

  private makeDraggable(
    el: HTMLElement,
    nodeId: string,
    nodeW: number,
    nodeH: number,
    freeDrag: boolean,
  ): void {
    let isDragging = false;
    let startX = 0;
    let startY = 0;
    let origLeft = 0;
    let origTop = 0;

    const onMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return; // left button only
      e.preventDefault();
      e.stopPropagation();

      isDragging = true;
      el.classList.add('dragging');
      el.style.zIndex = '20';

      // Convert right/bottom to left/top if they were used
      const computed = window.getComputedStyle(el);
      if (computed.right && computed.right !== 'auto') {
        const parentW = el.parentElement?.clientWidth || 0;
        const currentLeft = parentW - parseFloat(computed.right) - el.offsetWidth;
        el.style.left = `${currentLeft}px`;
        el.style.right = '';
      }
      if (computed.bottom && computed.bottom !== 'auto') {
        const parentH = el.parentElement?.clientHeight || 0;
        const currentTop = parentH - parseFloat(computed.bottom) - el.offsetHeight;
        el.style.top = `${currentTop}px`;
        el.style.bottom = '';
      }

      startX = e.clientX;
      startY = e.clientY;
      origLeft = parseFloat(el.style.left) || 0;
      origTop = parseFloat(el.style.top) || 0;

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    };

    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;
      e.preventDefault();

      const dx = e.clientX - startX;
      const dy = e.clientY - startY;

      const boundaryW = this.boundaryEl?.clientWidth || 0;
      const boundaryH = this.boundaryEl?.clientHeight || 0;

      // Update node width/height from actual DOM element
      const actualW = el.offsetWidth || nodeW;
      const actualH = el.offsetHeight || nodeH;

      let newLeft = origLeft + dx;
      let newTop = origTop + dy;

      if (freeDrag) {
        // Constrain to boundary interior with DRAG_MARGIN
        newLeft = Math.max(DRAG_MARGIN, Math.min(boundaryW - actualW - DRAG_MARGIN, newLeft));
        newTop = Math.max(DRAG_MARGIN, Math.min(boundaryH - actualH - DRAG_MARGIN, newTop));
      }

      el.style.left = `${newLeft}px`;
      el.style.top = `${newTop}px`;

      // Update position map
      this.nodePositions.set(nodeId, {
        x: newLeft,
        y: newTop,
        width: actualW,
        height: actualH,
      });

      // Re-render arrows during drag
      this.renderAllArrows();
    };

    const onMouseUp = () => {
      if (!isDragging) return;
      isDragging = false;
      el.classList.remove('dragging');
      el.style.zIndex = '';

      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);

      // Final arrow re-render with settled positions
      this.renderAllArrows();
    };

    el.addEventListener('mousedown', onMouseDown);
  }

  private makeSocketDraggable(
    el: HTMLElement,
    socketData: SocketData,
    containerW: number,
    containerH: number,
  ): void {
    let isDragging = false;
    let startX = 0;
    let startY = 0;
    let origLeft = 0;
    let origTop = 0;

    const isVertical = socketData.edge === 'left' || socketData.edge === 'right';

    const onMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return;
      e.preventDefault();
      e.stopPropagation();

      isDragging = true;
      el.classList.add('dragging');
      el.style.zIndex = '20';

      startX = e.clientX;
      startY = e.clientY;
      origLeft = parseFloat(el.style.left) || 0;
      origTop = parseFloat(el.style.top) || 0;

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    };

    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;
      e.preventDefault();

      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      const elW = el.offsetWidth || 80;
      const elH = el.offsetHeight || 20;

      let newLeft = origLeft;
      let newTop = origTop;

      if (isVertical) {
        // Vertical edge: only move along Y axis
        newTop = origTop + dy;
        newTop = Math.max(DRAG_MARGIN, Math.min(containerH - elH - DRAG_MARGIN, newTop));
      } else {
        // Horizontal edge: only move along X axis
        newLeft = origLeft + dx;
        newLeft = Math.max(DRAG_MARGIN, Math.min(containerW - elW - DRAG_MARGIN, newLeft));
      }

      el.style.left = `${newLeft}px`;
      el.style.top = `${newTop}px`;

      this.socketPositions.set(socketData.id, {
        x: newLeft,
        y: newTop,
        width: elW,
        height: elH,
      });

      this.renderAllArrows();
    };

    const onMouseUp = () => {
      if (!isDragging) return;
      isDragging = false;
      el.classList.remove('dragging');
      el.style.zIndex = '';

      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);

      this.renderAllArrows();
    };

    el.addEventListener('mousedown', onMouseDown);
  }

  // ── SVG Arrow Overlay ───────────────────────────────────────────────

  private createSvgOverlay(w: number, h: number): SVGSVGElement {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.classList.add('svg-overlay');
    svg.setAttribute('width', String(w));
    svg.setAttribute('height', String(h));
    svg.setAttribute('viewBox', `0 0 ${w} ${h}`);

    // Arrowhead marker definitions
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    defs.appendChild(this.createMarker(MARKER_ACCENT, 'var(--accent)'));
    defs.appendChild(this.createMarker(MARKER_AMBER, 'var(--status-warning)'));
    svg.appendChild(defs);

    return svg;
  }

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

  private renderAllArrows(): void {
    if (!this.svgOverlay) return;

    // Remove existing polylines (keep defs)
    const defs = this.svgOverlay.querySelector('defs');
    while (this.svgOverlay.lastChild && this.svgOverlay.lastChild !== defs) {
      this.svgOverlay.removeChild(this.svgOverlay.lastChild);
    }
    // Re-append defs first if it got displaced
    if (defs && this.svgOverlay.firstChild !== defs) {
      this.svgOverlay.insertBefore(defs, this.svgOverlay.firstChild);
    }

    // Update node positions from actual DOM elements
    this.syncNodePositions();

    // Render internal edges (accent, solid)
    for (const edge of this.internalEdges) {
      this.renderEdge(edge.source, edge.target, 'internal');
    }

    // Render socket edges (amber, dashed)
    for (const edge of this.socketEdges) {
      this.renderEdge(edge.source, edge.target, 'cross-boundary');
    }
  }

  private renderEdge(sourceId: string, targetId: string, type: 'internal' | 'cross-boundary'): void {
    const sourcePos = this.nodePositions.get(sourceId) || this.socketPositions.get(sourceId);
    const targetPos = this.nodePositions.get(targetId) || this.socketPositions.get(targetId);
    if (!sourcePos || !targetPos || !this.svgOverlay) return;

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
    if (!result.points) return;

    const polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
    polyline.setAttribute('points', result.points);
    polyline.setAttribute('fill', 'none');
    polyline.setAttribute('stroke-width', '1');
    polyline.dataset.sourceId = sourceId;
    polyline.dataset.targetId = targetId;
    polyline.dataset.edgeType = type;

    if (type === 'internal') {
      polyline.setAttribute('stroke', 'var(--accent)');
      polyline.setAttribute('opacity', '0.6');
      polyline.setAttribute('marker-end', `url(#${MARKER_ACCENT})`);
    } else {
      polyline.setAttribute('stroke', 'var(--status-warning)');
      polyline.setAttribute('opacity', '0.45');
      polyline.setAttribute('stroke-dasharray', '4 3');
      polyline.setAttribute('marker-end', `url(#${MARKER_AMBER})`);
    }

    this.svgOverlay.appendChild(polyline);
  }

  /** Synchronize node position map with actual DOM positions. */
  private syncNodePositions(): void {
    for (const [id, el] of this.classNodeElements) {
      const existing = this.nodePositions.get(id);
      if (existing) {
        existing.x = parseFloat(el.style.left) || existing.x;
        existing.y = parseFloat(el.style.top) || existing.y;
        existing.width = el.offsetWidth || existing.width;
        existing.height = el.offsetHeight || existing.height;
      }
    }
    for (const [id, el] of this.socketElements) {
      const existing = this.socketPositions.get(id);
      if (existing) {
        existing.x = parseFloat(el.style.left) || existing.x;
        existing.y = parseFloat(el.style.top) || existing.y;
        existing.width = el.offsetWidth || existing.width;
        existing.height = el.offsetHeight || existing.height;
      }
    }
  }

  // ── Impact Overlay ──────────────────────────────────────────────────

  private showImpactOverlay(nodeId: string): void {
    if (this.activeImpactNodeId === nodeId) return;
    this.clearImpactOverlay();

    this.activeImpactNodeId = nodeId;

    // Compute impact using internal edges
    const impact = computeImpact(this.internalEdges, nodeId);

    // Apply CSS classes to class nodes
    const selectedEl = this.classNodeElements.get(nodeId);
    if (selectedEl) {
      selectedEl.classList.add('impact-selected');
    }

    for (const [id, el] of this.classNodeElements) {
      if (id === nodeId) continue;
      if (impact.p0.has(id)) {
        el.classList.add('impact-p0');
      } else if (impact.p1.has(id)) {
        el.classList.add('impact-p1');
      } else if (impact.p2.has(id)) {
        el.classList.add('impact-p2');
      } else {
        el.classList.add('impact-dimmed');
      }
    }

    // Update SVG lines for impact visualization
    this.applyImpactToSvgLines(nodeId, impact);

    // Show legend
    if (this.impactLegend) {
      this.impactLegend.classList.add('visible');
    }
  }

  private clearImpactOverlay(): void {
    if (!this.activeImpactNodeId) return;
    this.activeImpactNodeId = null;

    // Remove all impact CSS classes from class nodes
    const impactClasses = ['impact-selected', 'impact-p0', 'impact-p1', 'impact-p2', 'impact-dimmed'];
    for (const el of this.classNodeElements.values()) {
      for (const cls of impactClasses) {
        el.classList.remove(cls);
      }
    }

    // Reset SVG lines to default styles
    this.resetSvgLines();

    // Hide legend
    if (this.impactLegend) {
      this.impactLegend.classList.remove('visible');
    }
  }

  private applyImpactToSvgLines(sourceId: string, impact: ImpactResult): void {
    if (!this.svgOverlay) return;

    const polylines = Array.from(this.svgOverlay.querySelectorAll('polyline'));
    for (const poly of polylines) {
      const el = poly as SVGElement;
      const srcId = el.dataset.sourceId || '';
      const tgtId = el.dataset.targetId || '';
      const edgeType = el.dataset.edgeType || 'internal';

      // Check if this edge is part of the impact propagation
      const isSourceImpacted = srcId === sourceId || impact.p0.has(srcId) || impact.p1.has(srcId) || impact.p2.has(srcId);
      const isTargetImpacted = tgtId === sourceId || impact.p0.has(tgtId) || impact.p1.has(tgtId) || impact.p2.has(tgtId);

      if (srcId === sourceId || tgtId === sourceId) {
        // Direct edge from/to source → accent thick
        el.setAttribute('stroke', 'var(--accent)');
        el.setAttribute('stroke-width', '2');
        el.setAttribute('opacity', '0.9');
        el.setAttribute('stroke-dasharray', '');
      } else if (isSourceImpacted && isTargetImpacted) {
        // Both ends in impact zone
        const srcLevel = this.getImpactLevel(srcId, sourceId, impact);
        const tgtLevel = this.getImpactLevel(tgtId, sourceId, impact);
        const level = Math.min(srcLevel, tgtLevel);

        if (level === 1) {
          // P0 connection
          el.setAttribute('stroke', 'var(--accent)');
          el.setAttribute('stroke-width', '1.5');
          el.setAttribute('opacity', '0.75');
          el.setAttribute('stroke-dasharray', '');
        } else if (level === 2) {
          // P1 connection
          el.setAttribute('stroke', 'var(--status-warning)');
          el.setAttribute('stroke-width', '1');
          el.setAttribute('opacity', '0.6');
          el.setAttribute('stroke-dasharray', '');
        } else {
          // P2 connection
          el.setAttribute('stroke', 'var(--status-warning)');
          el.setAttribute('stroke-width', '0.75');
          el.setAttribute('opacity', '0.45');
          el.setAttribute('stroke-dasharray', '4 3');
        }
      } else {
        // Not in impact zone → dim/invisible
        el.setAttribute('opacity', '0.08');
      }

      // Preserve cross-boundary marker references
      if (edgeType === 'internal') {
        el.setAttribute('marker-end', `url(#${MARKER_ACCENT})`);
      } else {
        el.setAttribute('marker-end', `url(#${MARKER_AMBER})`);
      }
    }
  }

  /** Returns impact level: 0=source, 1=P0, 2=P1, 3=P2, Infinity=none. */
  private getImpactLevel(nodeId: string, sourceId: string, impact: ImpactResult): number {
    if (nodeId === sourceId) return 0;
    if (impact.p0.has(nodeId)) return 1;
    if (impact.p1.has(nodeId)) return 2;
    if (impact.p2.has(nodeId)) return 3;
    return Infinity;
  }

  private resetSvgLines(): void {
    if (!this.svgOverlay) return;

    const polylines = Array.from(this.svgOverlay.querySelectorAll('polyline'));
    for (const poly of polylines) {
      const el = poly as SVGElement;
      const edgeType = el.dataset.edgeType || 'internal';

      el.setAttribute('stroke-width', '1');

      if (edgeType === 'internal') {
        el.setAttribute('stroke', 'var(--accent)');
        el.setAttribute('opacity', '0.6');
        el.setAttribute('stroke-dasharray', '');
        el.setAttribute('marker-end', `url(#${MARKER_ACCENT})`);
      } else {
        el.setAttribute('stroke', 'var(--status-warning)');
        el.setAttribute('opacity', '0.45');
        el.setAttribute('stroke-dasharray', '4 3');
        el.setAttribute('marker-end', `url(#${MARKER_AMBER})`);
      }
    }
  }

  // ── Impact Legend ───────────────────────────────────────────────────

  private createImpactLegend(): HTMLElement {
    const legend = document.createElement('div');
    legend.className = 'impact-legend';
    legend.setAttribute('role', 'complementary');
    legend.setAttribute('aria-label', 'Impact analysis legend');

    const items = [
      { cls: 'p0', label: 'P0 (direct)' },
      { cls: 'p1', label: 'P1 (transitive)' },
      { cls: 'p2', label: 'P2 (deep)' },
      { cls: 'dimmed', label: 'Not impacted' },
    ];

    for (const item of items) {
      const row = document.createElement('div');
      row.className = 'impact-legend-item';

      const swatch = document.createElement('span');
      swatch.className = `legend-swatch ${item.cls}`;

      const text = document.createElement('span');
      text.textContent = item.label;

      row.appendChild(swatch);
      row.appendChild(text);
      legend.appendChild(row);
    }

    return legend;
  }

  // ── Detail Panel ────────────────────────────────────────────────────

  private showDetailPanel(nodeData: ClassNodeData): void {
    if (!this.detailPanel) return;

    // Compute impact for blast radius
    const impact = computeImpact(this.internalEdges, nodeData.id);

    const blastRadius: BlastRadius = {
      p0: impact.p0.size,
      p1: impact.p1.size,
      p2: impact.p2.size,
      totalNodes: this.classNodes.length,
    };

    // Compute fan-in/fan-out
    let fanIn = 0;
    let fanOut = 0;
    for (const edge of this.internalEdges) {
      if (edge.target === nodeData.id) fanIn++;
      if (edge.source === nodeData.id) fanOut++;
    }
    // Also count socket edges for fan-in/fan-out
    for (const edge of this.socketEdges) {
      if (edge.target === nodeData.id) fanIn++;
      if (edge.source === nodeData.id) fanOut++;
    }

    const nodeDataForPanel: NodeData = {
      id: nodeData.id,
      shortLabel: nodeData.shortLabel,
      domain: nodeData.domain,
      metadata: nodeData.metadata,
    };

    this.detailPanel.show(nodeDataForPanel, blastRadius, fanIn, fanOut);
  }

  // ── Global Listeners ────────────────────────────────────────────────

  private installGlobalListeners(): void {
    // Esc key: clear impact + hide detail panel
    this.boundKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        this.clearImpactOverlay();
        if (this.detailPanel) {
          this.detailPanel.hide();
        }
      }
    };
    document.addEventListener('keydown', this.boundKeyDown);

    // Mouse leave boundary: clear impact
    if (this.boundaryEl) {
      this.boundBoundaryLeave = () => {
        this.clearImpactOverlay();
      };
      this.boundaryEl.addEventListener('mouseleave', this.boundBoundaryLeave);
    }
  }

  private uninstallGlobalListeners(): void {
    if (this.boundKeyDown) {
      document.removeEventListener('keydown', this.boundKeyDown);
      this.boundKeyDown = null;
    }
    if (this.boundBoundaryLeave && this.boundaryEl) {
      this.boundaryEl.removeEventListener('mouseleave', this.boundBoundaryLeave);
      this.boundBoundaryLeave = null;
    }
  }

  // ── Utility ─────────────────────────────────────────────────────────

  private estimateNodeWidth(label: string): number {
    return Math.max(CLASS_NODE_MIN_WIDTH, label.length * PX_PER_CHAR + CLASS_NODE_PADDING);
  }
}
