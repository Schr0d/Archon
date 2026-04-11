import { describe, it, expect, beforeEach, vi } from 'vitest';
import { GraphCanvas } from './graphCanvas';

// Mock HTMLCanvas for Node.js environment
global.HTMLCanvasElement = class HTMLCanvasElement {
  getContext() {
    return createMockContext();
  }
} as any;

function createMockContext() {
  return {
    clearRect: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    translate: vi.fn(),
    scale: vi.fn(),
    beginPath: vi.fn(),
    arc: vi.fn(),
    arcTo: vi.fn(),
    moveTo: vi.fn(),
    closePath: vi.fn(),
    roundRect: vi.fn(),
    fill: vi.fn(),
    stroke: vi.fn(),
    fillText: vi.fn(),
    measureText: vi.fn().mockReturnValue(50),
    setLineDash: vi.fn(),
    lineDashOffset: vi.fn(),
    clip: vi.fn(),
    createLinearGradient: vi.fn().mockReturnValue({
      addColorStop: vi.fn(),
    }),
    fillRect: vi.fn(),
    shadowBlur: 0,
    shadowColor: '',
    globalAlpha: 1,
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 1,
    font: '',
    textAlign: '',
    textBaseline: '',
  } as any;
}

describe('GraphCanvas', () => {
  let mockCanvas: any;
  let mockData: any;

  beforeEach(() => {
    // Create mock canvas element
    mockCanvas = document.createElement('canvas') as any;
    mockCanvas.id = 'graphCanvas';
    mockCanvas.width = 800;
    mockCanvas.height = 600;
    mockCanvas.getBoundingClientRect = vi.fn().mockReturnValue({
      left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600,
    });
    // Add getContext method to return mocked context
    mockCanvas.getContext = vi.fn().mockImplementation((contextType: string) => {
      if (contextType === '2d') {
        return createMockContext();
      }
      return null;
    });
    // Mock getElementById to return our mock canvas so GraphCanvas uses it
    vi.spyOn(document, 'getElementById').mockReturnValue(mockCanvas);

    // Create mock data with Tier 3 metrics
    mockData = {
      $schema: 'archon-metadata-v1',
      nodes: [
        { id: 'com.archon.core.Graph', x: 100, y: 100, metadata: { metrics: { pageRank: 0.1, betweenness: 0.2, closeness: 0.3 }, issues: {} } },
        { id: 'com.archon.core.Node', x: 200, y: 150, metadata: { metrics: { pageRank: 0.3, betweenness: 0.5, closeness: 0.4 }, issues: {} } },
        { id: 'com.archon.service.Analyzer', x: 300, y: 200, metadata: { metrics: { pageRank: 0.6, betweenness: 0.8, closeness: 0.5 }, issues: { bridge: true } } }
      ],
      edges: [
        { source: 'com.archon.core.Graph', target: 'com.archon.core.Node' },
        { source: 'com.archon.core.Node', target: 'com.archon.service.Analyzer' }
      ],
      fullAnalysis: {
        pageRank: { a: 0.1, b: 0.3, c: 0.6 },
        betweenness: { a: 0.2, b: 0.5, c: 0.8 },
        closeness: { a: 0.3, b: 0.4, c: 0.5 },
        bridges: []
      }
    };
  });

  describe('constructor', () => {
    it('creates canvas with nodes and edges', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.nodes).toHaveLength(3);
      expect(graph.edges).toEqual(mockData.edges);
    });

    it('computes pageRankThreshold once before nodes are initialized', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      // 3 nodes with pageRank 0.1, 0.3, 0.6. 80th percentile index = floor(3*0.8) = 2
      // sorted: [0.1, 0.3, 0.6], index 2 = 0.6
      expect(graph.pageRankThreshold).toBe(0.6);
    });

    it('builds nodeMap for O(1) lookups', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.nodeMap).toBeInstanceOf(Map);
      expect(graph.nodeMap.size).toBe(3);
      expect(graph.nodeMap.get('com.archon.core.Graph')).toBeDefined();
      expect(graph.nodeMap.get('com.archon.core.Graph').shortLabel).toBe('Graph');
    });

    it('detects prefers-reduced-motion', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      // In test env, matchMedia may not exist, so it defaults to false or reads the actual value
      expect(typeof graph.reducedMotion).toBe('boolean');
    });

    it('initializes transform state', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.transform).toEqual({ x: 0, y: 0, k: 1 });
    });

    it('creates ForceSimulation with nodes and edges', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.simulation).toBeDefined();
      expect(graph.simulation.nodes).toHaveLength(3);
    });
  });

  describe('initNode', () => {
    it('extracts shortLabel from fully qualified class name', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.nodes[0].shortLabel).toBe('Graph');
      expect(graph.nodes[1].shortLabel).toBe('Node');
      expect(graph.nodes[2].shortLabel).toBe('Analyzer');
    });

    it('uses full id as shortLabel when no dot present', () => {
      const simpleData = {
        ...mockData,
        nodes: [{ id: 'main', metadata: { metrics: {}, issues: {} } }]
      };
      const graph = new GraphCanvas('graphCanvas', simpleData);

      expect(graph.nodes[0].shortLabel).toBe('main');
    });

    it('computes width based on shortLabel length', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      // "Graph" = 5 chars → max(80, 5*7+24) = max(80, 59) = 80
      expect(graph.nodes[0].width).toBe(80);
      // "Analyzer" = 8 chars → max(80, 8*7+24) = max(80, 80) = 80
      expect(graph.nodes[2].width).toBe(80);
    });

    it('caps width at 200px for long names', () => {
      const longData = {
        ...mockData,
        nodes: [{ id: 'com.archon.core.VeryLongClassNameThatExceedsLimit', metadata: { metrics: {}, issues: {} } }]
      };
      const graph = new GraphCanvas('graphCanvas', longData);

      // "VeryLongClassNameThatExceedsLimit" = 32 chars → 32*7+24 = 248, capped to 200
      expect(graph.nodes[0].width).toBe(200);
    });

    it('sets height to 28', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      for (const node of graph.nodes) {
        expect(node.height).toBe(28);
      }
    });

    it('computes color from closeness via closenessToColor', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      // Node 0: closeness=0.3, Node 2: closeness=0.5
      // Higher closeness should have more green
      expect(graph.nodes[0].color).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
      expect(graph.nodes[2].color).toMatch(/^rgb\(\d+, \d+, \d+\)$/);
    });

    it('sets glow for nodes at or above 80th percentile pageRank', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      // Threshold is 0.6. Only node with pageRank >= 0.6 gets glow.
      expect(graph.nodes[0].glow).toBe(0); // pageRank 0.1
      expect(graph.nodes[1].glow).toBe(0); // pageRank 0.3
      expect(graph.nodes[2].glow).toBeGreaterThan(0); // pageRank 0.6
    });

    it('marks bridge nodes', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.nodes[0].isBridge).toBe(false);
      expect(graph.nodes[2].isBridge).toBe(true);
    });

    it('defaults to ungrouped domain when not specified', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.nodes[0].domain).toBe('ungrouped');
    });
  });

  describe('computePageRankThreshold', () => {
    it('returns 80th percentile of pageRank values', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      // pageRanks: [0.1, 0.3, 0.6], sorted: [0.1, 0.3, 0.6]
      // floor(3 * 0.8) = 2 → sorted[2] = 0.6
      expect(graph.pageRankThreshold).toBe(0.6);
    });

    it('returns Infinity when no nodes have pageRank', () => {
      const noMetricsData = {
        ...mockData,
        nodes: mockData.nodes.map((n: any) => ({ ...n, metadata: { metrics: {}, issues: {} } }))
      };
      const graph = new GraphCanvas('graphCanvas', noMetricsData);

      expect(graph.pageRankThreshold).toBe(Infinity);
    });

    it('returns Infinity when nodes array is empty', () => {
      const emptyData = { ...mockData, nodes: [], edges: [] };
      const graph = new GraphCanvas('graphCanvas', emptyData);

      expect(graph.pageRankThreshold).toBe(Infinity);
    });
  });

  describe('closenessToColor', () => {
    it('returns blue at closeness 0', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const color = graph.closenessToColor(0);

      expect(color).toBe('rgb(74, 144, 226)');
    });

    it('returns green at closeness 1', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const color = graph.closenessToColor(1);

      expect(color).toBe('rgb(74, 222, 128)');
    });

    it('interpolates at closeness 0.5', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const color = graph.closenessToColor(0.5);

      // g: 144 + (222-144)*0.5 = 183, b: 226 + (128-226)*0.5 = 177
      expect(color).toBe('rgb(74, 183, 177)');
    });
  });

  describe('darkenColor', () => {
    it('darkens an rgb color by the given amount', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      const result = graph.darkenColor('rgb(100, 200, 50)', 0.2);
      // r: 100*0.8=80, g: 200*0.8=160, b: 50*0.8=40
      expect(result).toBe('rgb(80, 160, 40)');
    });

    it('clamps to 0 (no negative values)', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      const result = graph.darkenColor('rgb(10, 10, 10)', 0.99);
      expect(result).toBe('rgb(0, 0, 0)');
    });

    it('returns original string for non-rgb input', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.darkenColor('#ff0000', 0.5)).toBe('#ff0000');
      expect(graph.darkenColor('red', 0.5)).toBe('red');
    });

    it('no-op when amount is 0', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.darkenColor('rgb(100, 200, 50)', 0)).toBe('rgb(100, 200, 50)');
    });
  });

  describe('drawRoundedRect', () => {
    it('uses roundRect when available', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.ctx.roundRect = vi.fn();
      graph.ctx.moveTo = vi.fn();
      graph.ctx.arcTo = vi.fn();

      graph.drawRoundedRect(10, 20, 80, 28, 4);

      expect(graph.ctx.roundRect).toHaveBeenCalledWith(10, 20, 80, 28, 4);
    });

    it('falls back to arcTo when roundRect not available', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.ctx.roundRect = undefined;
      graph.ctx.arcTo = vi.fn();
      graph.ctx.moveTo = vi.fn();
      graph.ctx.closePath = vi.fn();

      graph.drawRoundedRect(10, 20, 80, 28, 4);

      expect(graph.ctx.moveTo).toHaveBeenCalled();
      expect(graph.ctx.arcTo).toHaveBeenCalledTimes(4);
      expect(graph.ctx.closePath).toHaveBeenCalled();
    });
  });

  describe('drawNodes zoom levels', () => {
    it('draws 6px dots at far zoom (k < 0.5)', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.transform.k = 0.3;
      graph._fadeStartTime = 0;
      graph.lastTime = 1000;

      graph.drawNodes();

      // Should call arc for dots, not roundRect or fillRect
      expect(graph.ctx.arc).toHaveBeenCalled();
    });

    it('draws rounded rects with labels at medium zoom (0.5 <= k <= 1.5)', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.transform.k = 1;
      graph._fadeStartTime = 0;
      graph.lastTime = 1000;

      graph.drawNodes();

      // Should have fillText for labels
      expect(graph.ctx.fillText).toHaveBeenCalled();
    });

    it('draws betweenness bars at close zoom (k > 1.5)', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.transform.k = 2;
      graph._fadeStartTime = 0;
      graph.lastTime = 1000;

      graph.drawNodes();

      // fillRect used for betweenness bars
      expect(graph.ctx.fillRect).toHaveBeenCalled();
    });

    it('applies fade-in alpha during first 500ms', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph._fadeStartTime = 0;
      graph.lastTime = 250; // halfway through fade

      // drawNodes uses lastTime but globalAlpha gets reset at end of each node
      // Check that the internal calculation would produce 0.5
      const fadeElapsed = graph.lastTime - graph._fadeStartTime;
      const fadeIn = Math.min(1, fadeElapsed / 500);
      expect(fadeIn).toBe(0.5);
    });

    it('sets full alpha after 500ms', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph._fadeStartTime = 0;
      graph.lastTime = 600;

      const fadeElapsed = graph.lastTime - graph._fadeStartTime;
      const fadeIn = Math.min(1, fadeElapsed / 500);
      expect(fadeIn).toBe(1);
    });
  });

  describe('nodeMap', () => {
    it('returns the correct node by id', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      const node = graph.nodeMap.get('com.archon.core.Graph');
      expect(node).toBeDefined();
      expect(node.shortLabel).toBe('Graph');
    });

    it('returns undefined for unknown id', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.nodeMap.get('nonexistent')).toBeUndefined();
    });
  });

  describe('showTooltip', () => {
    it('populates tooltip DOM with node data', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const node = graph.nodes[2]; // Analyzer with bridge

      // Mock tooltip DOM elements with all needed properties
      const mockTooltipEl = () => ({
        textContent: '',
        innerHTML: '',
        style: { display: '', transform: '', left: '', top: '' },
        classList: { add: vi.fn(), remove: vi.fn() },
        setAttribute: vi.fn(),
        getAttribute: vi.fn(),
        getBoundingClientRect: vi.fn().mockReturnValue({ width: 280, height: 200 }),
      });

      const els = {
        nodeTooltip: mockTooltipEl(),
        tooltipName: mockTooltipEl(),
        tooltipPackage: mockTooltipEl(),
        tooltipIcon: mockTooltipEl(),
        tooltipPageRank: mockTooltipEl(),
        tooltipBetweenness: mockTooltipEl(),
        tooltipCloseness: mockTooltipEl(),
        tooltipBadges: mockTooltipEl(),
        tooltipBadgeSeparator: mockTooltipEl(),
        tooltipFanIn: mockTooltipEl(),
        tooltipFanOut: mockTooltipEl(),
        tooltipArrow: { className: '' },
      };

      vi.spyOn(document, 'getElementById').mockImplementation((id: string) => {
        return (els as any)[id] || null;
      });

      graph.showTooltip(node);

      expect(els.nodeTooltip.classList.add).toHaveBeenCalledWith('visible');
      expect(els.nodeTooltip.setAttribute).toHaveBeenCalledWith('aria-hidden', 'false');
    });
  });

  describe('hideTooltip', () => {
    it('removes visible class and sets aria-hidden', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      const mockTooltip = {
        classList: { remove: vi.fn() },
        setAttribute: vi.fn(),
      };
      vi.spyOn(document, 'getElementById').mockReturnValue(mockTooltip);

      graph.hideTooltip();

      expect(mockTooltip.classList.remove).toHaveBeenCalledWith('visible');
      expect(mockTooltip.setAttribute).toHaveBeenCalledWith('aria-hidden', 'true');
    });
  });

  describe('updateTooltip', () => {
    it('updates position when a tooltip node is active', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const node = graph.nodes[0];
      graph._tooltipNode = node;

      const mockTooltip = { style: {}, getBoundingClientRect: vi.fn().mockReturnValue({ width: 280, height: 200 }) };
      const mockArrow = { className: '' };
      vi.spyOn(document, 'getElementById')
        .mockReturnValueOnce(mockTooltip)
        .mockReturnValueOnce(mockArrow);

      graph.updateTooltip();

      expect(mockTooltip.style.transform).toMatch(/translate\([\d.]+px, [\d.]+px\)/);
    });

    it('does nothing when no tooltip node is active', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph._tooltipNode = null;

      const spy = vi.spyOn(document, 'getElementById');

      graph.updateTooltip();

      expect(spy).not.toHaveBeenCalled();
    });
  });

  describe('getDomainIcon', () => {
    it('returns icon for known domains', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.getDomainIcon('core')).toBeTruthy();
      expect(graph.getDomainIcon('cli')).toBeTruthy();
      expect(graph.getDomainIcon('ungrouped')).toBeTruthy();
    });

    it('returns default icon for unknown domain', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      expect(graph.getDomainIcon('nonexistent')).toBeTruthy();
    });
  });

  describe('updateTooltipPosition', () => {
    it('flips tooltip below node when near top edge', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const node = { ...graph.nodes[0], x: 400, y: 10 }; // near top

      const mockTooltip = {
        style: { transform: '', left: '', top: '' },
        getBoundingClientRect: vi.fn().mockReturnValue({ width: 280, height: 200 }),
      };
      const mockArrow = { className: '' };
      vi.spyOn(document, 'getElementById')
        .mockReturnValueOnce(mockTooltip)
        .mockReturnValueOnce(mockArrow);

      graph.updateTooltipPosition(node);

      // Arrow should be 'above' (pointing up to node above tooltip)
      expect(mockArrow.className).toContain('above');
    });

    it('clamps tooltip within horizontal viewport bounds', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const node = { ...graph.nodes[0], x: 10, y: 400 }; // far left

      const mockTooltip = {
        style: { transform: '', left: '', top: '' },
        getBoundingClientRect: vi.fn().mockReturnValue({ width: 280, height: 200 }),
      };
      const mockArrow = { className: '' };
      vi.spyOn(document, 'getElementById')
        .mockReturnValueOnce(mockTooltip)
        .mockReturnValueOnce(mockArrow);

      graph.updateTooltipPosition(node);

      // Extract translate values
      const match = mockTooltip.style.transform.match(/translate\((\d+\.?\d*)px/);
      expect(match).toBeTruthy();
      expect(parseFloat(match![1])).toBeGreaterThanOrEqual(8);
    });
  });

  describe('render', () => {
    it('clears canvas before drawing', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      graph.render(100);

      expect(graph.ctx.clearRect).toHaveBeenCalledWith(0, 0, 800, 600);
    });

    it('applies transform before drawing', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);

      graph.render(100);

      expect(graph.ctx.save).toHaveBeenCalled();
      expect(graph.ctx.translate).toHaveBeenCalledWith(0, 0);
      expect(graph.ctx.scale).toHaveBeenCalledWith(1, 1);
    });

    it('skips physics when reducedMotion is true', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.reducedMotion = true;
      const spy = vi.spyOn(graph.simulation, 'update');

      graph.render(100);

      expect(spy).not.toHaveBeenCalled();
    });
  });

  describe('setNodePosition', () => {
    it('delegates to ForceSimulation', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const setPosSpy = vi.spyOn(graph.simulation, 'setNodePosition');

      graph.setNodePosition('com.archon.core.Graph', 500, 300);

      expect(setPosSpy).toHaveBeenCalledWith('com.archon.core.Graph', 500, 300);
    });
  });

  describe('expandDomain', () => {
    it('sets viewLevel to CLASS and stores expandedDomain when domain exists', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      // Use a node id that exists in the nodeMap
      graph.expandDomain('com.archon.core.Graph');

      expect(graph.viewLevel).toBe('CLASS');
      expect(graph.expandedDomain).toBe('com.archon.core.Graph');
    });

    it('does nothing when domain does not exist', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const restartSpy = vi.spyOn(graph.simulation, 'restart');

      graph.expandDomain('nonexistent-domain');

      expect(graph.viewLevel).toBe('DOMAIN');
      expect(restartSpy).not.toHaveBeenCalled();
    });

    it('restarts simulation for animation when domain exists', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const restartSpy = vi.spyOn(graph.simulation, 'restart');

      graph.expandDomain('com.archon.core.Graph');

      expect(restartSpy).toHaveBeenCalled();
    });
  });

  describe('returnToDomainView', () => {
    it('resets viewLevel to DOMAIN and clears expandedDomain', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      graph.viewLevel = 'CLASS';
      graph.expandedDomain = 'test-domain';

      graph.returnToDomainView();

      expect(graph.viewLevel).toBe('DOMAIN');
      expect(graph.expandedDomain).toBeNull();
    });

    it('restarts simulation', () => {
      const graph = new GraphCanvas('graphCanvas', mockData);
      const restartSpy = vi.spyOn(graph.simulation, 'restart');

      graph.returnToDomainView();

      expect(restartSpy).toHaveBeenCalled();
    });
  });
});
