/**
 * graphController.ts -- Mode state machine orchestrating Mode 1 (Domain Overview)
 * and Mode 2 (Boundary Drill-down).
 *
 * Manages current mode, instantiates view classes, handles mode transitions,
 * wires the sidebar, and provides keyboard shortcuts (Esc).
 *
 * All DOM construction uses createElement/textContent -- no innerHTML.
 */

import { Mode1DomainView, DomainInfo, InterDomainEdge } from './mode1DomainView';
import { Mode2BoundaryView, ClassNodeData, SocketData } from './mode2BoundaryView';

// ── Types ─────────────────────────────────────────────────────────────

type Mode = 'MODE_1' | 'MODE_2';

// ── Implementation ────────────────────────────────────────────────────

export class GraphController {
  private viewport: HTMLElement;
  private sidebar: HTMLElement;
  private data: any;

  /** Current mode state. */
  private mode: Mode = 'MODE_1';

  /** Active domain in MODE_2, null in MODE_1. */
  private activeDomainId: string | null = null;

  /** Current view instance. */
  private currentView: Mode1DomainView | Mode2BoundaryView | null = null;

  /** Precomputed domain data. */
  private domains: Map<string, DomainInfo>;
  private interDomainEdges: InterDomainEdge[];

  /** Maps node id -> domain id for fast edge classification. */
  private nodeDomainMap: Map<string, string>;

  /** Bound handlers for cleanup. */
  private boundSidebarClick: (e: MouseEvent) => void;
  private boundKeyDown: (e: KeyboardEvent) => void;

  constructor(
    viewport: HTMLElement,
    sidebar: HTMLElement,
    data: any,
  ) {
    this.viewport = viewport;
    this.sidebar = sidebar;
    this.data = data;

    // Build domain data from graph
    this.nodeDomainMap = new Map();
    this.domains = new Map();
    this.interDomainEdges = [];

    this.processData();

    // Bind handlers for cleanup
    this.boundSidebarClick = this.onSidebarClick.bind(this);
    this.boundKeyDown = this.onKeyDown.bind(this);
  }

  // ── Public API ──────────────────────────────────────────────────────

  /** Initialize and render the default mode (MODE_1). */
  start(): void {
    this.installEventListeners();
    this.transitionToMode1();
    this.updateStatusBar();
  }

  /** Tear down: remove all event listeners and destroy current view. */
  destroy(): void {
    this.sidebar.removeEventListener('click', this.boundSidebarClick);
    document.removeEventListener('keydown', this.boundKeyDown);

    if (this.currentView) {
      this.currentView.destroy();
      this.currentView = null;
    }

    this.clearViewport();
  }

  // ── Data Processing ─────────────────────────────────────────────────

  private processData(): void {
    // Group nodes by domain
    for (const node of this.data.nodes) {
      const domainId = node.domain || 'ungrouped';
      this.nodeDomainMap.set(node.id, domainId);

      if (!this.domains.has(domainId)) {
        this.domains.set(domainId, {
          id: domainId,
          name: domainId,
          nodeCount: 0,
          health: 'healthy',
        });
      }
      this.domains.get(domainId)!.nodeCount++;
    }

    // Compute health for each domain
    for (const [domainId, domain] of this.domains) {
      const domainNodes = this.data.nodes.filter(
        (n: any) => (n.domain || 'ungrouped') === domainId,
      );
      domain.health = this.computeDomainHealth(domainNodes);
    }

    // Compute inter-domain edges
    const seen = new Set<string>();
    for (const edge of this.data.edges) {
      const sourceDomain = this.nodeDomainMap.get(edge.source);
      const targetDomain = this.nodeDomainMap.get(edge.target);
      if (!sourceDomain || !targetDomain) continue;
      if (sourceDomain === targetDomain) continue;

      // Deduplicate domain-to-domain edges
      const key = `${sourceDomain}->${targetDomain}`;
      if (seen.has(key)) continue;
      seen.add(key);

      this.interDomainEdges.push({
        source: sourceDomain,
        target: targetDomain,
      });
    }
  }

  // ── Mode Transitions ────────────────────────────────────────────────

  private transitionToMode1(): void {
    // Destroy current view
    if (this.currentView) {
      this.currentView.destroy();
      this.currentView = null;
    }

    this.clearViewport();

    this.mode = 'MODE_1';
    this.activeDomainId = null;
    this.updateSidebarActiveState();

    // Create Mode 1 view
    const domainList = Array.from(this.domains.values());
    const view = new Mode1DomainView(
      this.viewport,
      domainList,
      this.interDomainEdges,
      (domainId: string) => this.transitionToMode2(domainId),
    );

    view.render();
    this.currentView = view;

    this.updateStatusBar();
  }

  private transitionToMode2(domainId: string): void {
    // Destroy current view
    if (this.currentView) {
      this.currentView.destroy();
      this.currentView = null;
    }

    this.clearViewport();

    this.mode = 'MODE_2';
    this.activeDomainId = domainId;
    this.updateSidebarActiveState();

    // Get class nodes for this domain
    const classNodes: ClassNodeData[] = this.data.nodes
      .filter((n: any) => (n.domain || 'ungrouped') === domainId)
      .map((n: any) => ({
        id: n.id,
        shortLabel: n.id.includes('.')
          ? n.id.substring(n.id.lastIndexOf('.') + 1)
          : n.id,
        domain: domainId,
        metadata: n.metadata,
      }));

    // Get internal edges (both endpoints in same domain)
    const internalEdges = this.data.edges.filter((e: any) => {
      const srcDomain = this.nodeDomainMap.get(e.source);
      const tgtDomain = this.nodeDomainMap.get(e.target);
      return srcDomain === domainId && tgtDomain === domainId;
    }).map((e: any) => ({ source: e.source, target: e.target }));

    // Compute sockets
    const { sockets, socketEdges } = this.computeSockets(domainId);

    // Create Mode 2 view
    const domainLabel = this.domains.get(domainId)?.name || domainId;
    const view = new Mode2BoundaryView(
      this.viewport,
      domainId,
      domainLabel,
      classNodes,
      internalEdges,
      sockets,
      socketEdges,
      () => this.transitionToMode1(),
    );

    view.render();
    this.currentView = view;

    this.updateStatusBar();
  }

  // ── Socket Computation ──────────────────────────────────────────────

  private computeSockets(
    domainId: string,
  ): { sockets: SocketData[]; socketEdges: Array<{ source: string; target: string }> } {
    const sockets: SocketData[] = [];
    const socketEdges: Array<{ source: string; target: string }> = [];

    let socketIndex = 0;
    for (const edge of this.data.edges) {
      const sourceDomain = this.nodeDomainMap.get(edge.source);
      const targetDomain = this.nodeDomainMap.get(edge.target);
      if (!sourceDomain || !targetDomain) continue;

      if (sourceDomain === domainId && targetDomain !== domainId) {
        // Outgoing: socket on right edge
        const socketId = `socket-out-${socketIndex++}`;
        sockets.push({
          id: socketId,
          label: edge.target.split('.').pop() || edge.target,
          edge: 'right',
          type: 'out',
          connectedToClassId: edge.source,
        });
        socketEdges.push({ source: edge.source, target: socketId });
      } else if (targetDomain === domainId && sourceDomain !== domainId) {
        // Incoming: socket on left edge
        const socketId = `socket-in-${socketIndex++}`;
        sockets.push({
          id: socketId,
          label: edge.source.split('.').pop() || edge.source,
          edge: 'left',
          type: 'in',
          connectedToClassId: edge.target,
        });
        socketEdges.push({ source: socketId, target: edge.target });
      }
    }

    return { sockets, socketEdges };
  }

  // ── Domain Health ───────────────────────────────────────────────────

  private computeDomainHealth(
    nodes: any[],
  ): 'healthy' | 'warning' | 'error' {
    const hotspotIds = new Set((this.data.hotspots || []).map((h: any) => h.id));
    const cycleNodeIds = new Set<string>();
    for (const cycle of this.data.cycles || []) {
      for (const nodeId of cycle) {
        cycleNodeIds.add(nodeId);
      }
    }

    let hasErrors = false;
    let hasWarnings = false;

    for (const node of nodes) {
      if (hotspotIds.has(node.id) || cycleNodeIds.has(node.id)) {
        hasErrors = true;
      } else if (
        node.metadata?.metrics?.riskLevel === 'high' ||
        (node.metadata?.issues?.blindSpots?.length ?? 0) > 0 ||
        node.metadata?.issues?.bridge
      ) {
        hasWarnings = true;
      }
    }

    return hasErrors ? 'error' : hasWarnings ? 'warning' : 'healthy';
  }

  // ── Event Handlers ──────────────────────────────────────────────────

  private installEventListeners(): void {
    // Sidebar: event delegation for domain clicks
    this.sidebar.addEventListener('click', this.boundSidebarClick);

    // Keyboard: Esc to go back
    document.addEventListener('keydown', this.boundKeyDown);
  }

  private onSidebarClick(e: MouseEvent): void {
    const target = e.target as HTMLElement;
    const domainNode = target.closest('.domain-node') as HTMLElement | null;
    if (!domainNode) return;

    const domainId = domainNode.dataset.domain;
    if (!domainId) return;

    // If clicking the already-active domain in MODE_2, go back to MODE_1
    if (this.mode === 'MODE_2' && this.activeDomainId === domainId) {
      this.transitionToMode1();
    } else {
      this.transitionToMode2(domainId);
    }
  }

  private onKeyDown(e: KeyboardEvent): void {
    if (e.key === 'Escape' && this.mode === 'MODE_2') {
      this.transitionToMode1();
    }
  }

  // ── Sidebar Active State ────────────────────────────────────────────

  private updateSidebarActiveState(): void {
    const domainNodes = this.sidebar.querySelectorAll('.domain-node');
    for (const el of domainNodes) {
      const nodeEl = el as HTMLElement;
      const domainId = nodeEl.dataset.domain;
      if (this.mode === 'MODE_2' && domainId === this.activeDomainId) {
        nodeEl.classList.add('active');
      } else {
        nodeEl.classList.remove('active');
      }
    }
  }

  // ── Status Bar ──────────────────────────────────────────────────────

  private updateStatusBar(): void {
    const statusItems = document.querySelectorAll('.status-left .status-item');
    if (statusItems.length < 3) return;

    const modulesEl = statusItems[0]?.querySelector('.status-value');
    const classesEl = statusItems[1]?.querySelector('.status-value');
    const depsEl = statusItems[2]?.querySelector('.status-value');

    if (this.mode === 'MODE_1') {
      // Show global counts
      if (modulesEl) modulesEl.textContent = String(this.domains.size);
      if (classesEl) classesEl.textContent = String(this.data.nodes?.length ?? 0);
      if (depsEl) depsEl.textContent = String(this.data.edges?.length ?? 0);
    } else {
      // Show domain-level counts
      const domainId = this.activeDomainId;
      const domainNodeCount = domainId
        ? this.data.nodes.filter(
            (n: any) => (n.domain || 'ungrouped') === domainId,
          ).length
        : 0;
      const domainEdgeCount = domainId
        ? this.data.edges.filter((e: any) => {
            const srcDomain = this.nodeDomainMap.get(e.source);
            const tgtDomain = this.nodeDomainMap.get(e.target);
            return (
              srcDomain === domainId || tgtDomain === domainId
            );
          }).length
        : 0;

      if (modulesEl) modulesEl.textContent = '1';
      if (classesEl) classesEl.textContent = String(domainNodeCount);
      if (depsEl) depsEl.textContent = String(domainEdgeCount);
    }

    // Hotspot badge
    const hotspotBadge = document.getElementById('hotspotBadge');
    const hotspotCountEl = document.getElementById('hotspotCount');
    if (hotspotBadge && hotspotCountEl) {
      const numHotspots = this.data.hotspots?.length ?? 0;
      hotspotCountEl.textContent = String(numHotspots);
      hotspotBadge.style.display = numHotspots > 0 ? 'flex' : 'none';
    }

    // Cycle badge
    const cycleBadge = document.getElementById('cycleBadge');
    const cycleCountEl = document.getElementById('cycleCount');
    if (cycleBadge && cycleCountEl) {
      const numCycles = this.data.cycles?.length ?? 0;
      cycleCountEl.textContent = String(numCycles);
      cycleBadge.style.display = numCycles > 0 ? 'flex' : 'none';
    }
  }

  // ── Utility ─────────────────────────────────────────────────────────

  private clearViewport(): void {
    while (this.viewport.firstChild) {
      this.viewport.removeChild(this.viewport.firstChild);
    }
  }
}
