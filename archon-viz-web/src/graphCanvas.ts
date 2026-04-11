/**
 * GraphCanvas: Canvas-based graph renderer with Tier 3 metrics visualization
 */

/**
 * Force-directed physics simulation with Verlet integration
 */
class ForceSimulation {
    nodes: any[];
    edges: any[];
    nodeMap: Map<string, any>;
    private nodeCount: number;

    // Physics constants — scale with graph size for good layouts
    private readonly REPULSION_STRENGTH: number;
    private readonly IDEAL_EDGE_LENGTH: number;
    private readonly SPRING_CONSTANT = 0.6;
    private readonly DAMPING = 0.85;
    private readonly COOLING_RATE: number;
    private readonly MIN_TEMPERATURE = 0.001;
    private readonly MAX_ITERATIONS: number;

    temperature: number;
    active: boolean;
    iteration: number;

    constructor(nodes: any[], edges: any[]) {
        this.nodes = nodes.map(n => ({ ...n, vx: 0, vy: 0, fx: 0, fy: 0, mass: n.mass || 1 }));
        this.edges = edges;
        this.nodeCount = nodes.length;

        // Scale physics with graph size for good layouts
        this.REPULSION_STRENGTH = Math.max(500, nodes.length * nodes.length * 0.8);
        this.IDEAL_EDGE_LENGTH = nodes.length > 50 ? 120 : 80;
        this.COOLING_RATE = nodes.length > 50 ? 0.997 : 0.95;
        this.MAX_ITERATIONS = nodes.length > 50 ? 1500 : 500;
        this.nodeMap = new Map(this.nodes.map(n => [n.id, n]));

        this.temperature = 1.0;
        this.active = true;
        this.iteration = 0;
    }

    update(): boolean {
        if (!this.active) return false;

        // Clear forces
        for (const node of this.nodes) {
            node.fx = 0;
            node.fy = 0;
        }

        this.applyRepulsion();
        this.applyAttraction();
        this.applyForces();
        this.cool();

        return this.active;
    }

    private applyRepulsion(): void {
        for (let i = 0; i < this.nodes.length; i++) {
            for (let j = i + 1; j < this.nodes.length; j++) {
                const a = this.nodes[i];
                const b = this.nodes[j];
                const dx = b.x - a.x;
                const dy = b.y - a.y;
                const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                const force = this.REPULSION_STRENGTH / dist;
                const fx = (dx / dist) * force;
                const fy = (dy / dist) * force;
                a.fx -= fx;
                a.fy -= fy;
                b.fx += fx;
                b.fy += fy;
            }
        }
    }

    private applyAttraction(): void {
        for (const edge of this.edges) {
            const source = this.nodeMap.get(edge.source);
            const target = this.nodeMap.get(edge.target);
            if (!source || !target) continue;

            const dx = target.x - source.x;
            const dy = target.y - source.y;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const force = (dist * dist) / (this.IDEAL_EDGE_LENGTH * this.IDEAL_EDGE_LENGTH) * this.SPRING_CONSTANT;
            const fx = (dx / dist) * force;
            const fy = (dy / dist) * force;

            source.fx += fx;
            source.fy += fy;
            target.fx -= fx;
            target.fy -= fy;
        }
    }

    private applyForces(): void {
        for (const node of this.nodes) {
            node.vx = (node.vx + node.fx) * this.DAMPING;
            node.vy = (node.vy + node.fy) * this.DAMPING;
            node.x += node.vx;
            node.y += node.vy;
        }
    }

    private cool(): void {
        this.temperature *= this.COOLING_RATE;
        this.iteration++;

        if (this.temperature < this.MIN_TEMPERATURE || this.iteration > this.MAX_ITERATIONS) {
            this.active = false;
        }
    }

    restart(): void {
        this.active = true;
        this.temperature = 1.0;
        this.iteration = 0;
    }

    setNodePosition(nodeId: string, x: number, y: number): void {
        const node = this.nodeMap.get(nodeId);
        if (node) {
            node.x = x;
            node.y = y;
            node.vx = 0;
            node.vy = 0;
        }
    }
}

/**
 * GraphCanvas: Canvas-based graph renderer with Tier 3 metrics visualization
 */
export class GraphCanvas {
    nodes: any[];
    edges: any[];
    data: any;
    nodeMap: Map<string, any>;
    simulation: ForceSimulation;
    canvas: HTMLCanvasElement;
    ctx: CanvasRenderingContext2D;
    pageRankThreshold: number;
    reducedMotion: boolean;
    _fadeStartTime: number;
    _tooltipNode: any | null;
    lastTime: number;

    // View state
    viewLevel: 'DOMAIN' | 'CLASS';
    expandedDomain: string | null;
    selectedNode: any | null;
    hoveredNode: any | null;

    // Transform
    transform: { x: number; y: number; k: number };
    isDragging: boolean;
    isPanning: boolean;
    lastMouse: { x: number; y: number };

    constructor(canvasId: string, data: any) {
        this.canvas = document.getElementById(canvasId) as HTMLCanvasElement;
        this.ctx = this.canvas.getContext('2d')!;
        this.data = data;
        this.pageRankThreshold = this.computePageRankThreshold();
        this.nodes = data.nodes.map((n: any, i: number) => this.initNode(n, i, data.nodes.length));
        this.nodeMap = new Map(this.nodes.map((n: any) => [n.id, n]));
        this.edges = data.edges;

        // View state
        this.viewLevel = 'DOMAIN';
        this.expandedDomain = null;
        this.selectedNode = null;
        this.hoveredNode = null;

        // Transform
        this.transform = { x: 0, y: 0, k: 1 };
        this.isDragging = false;
        this.isPanning = false;
        this.lastMouse = { x: 0, y: 0 };

        // Animation
        this.lastTime = 0;
        this._fadeStartTime = 0;
        this._tooltipNode = null;

        // Reduced motion
        this.reducedMotion = typeof window !== 'undefined' && window.matchMedia
            ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
            : false;

        // Physics simulation
        this.simulation = new ForceSimulation(this.nodes, this.edges);
    }

    initNode(nodeData: any, index: number = 0, total: number = 1): any {
        const pageRank = nodeData.metadata?.metrics?.pageRank || 0;
        const betweenness = nodeData.metadata?.metrics?.betweenness || 0;
        const closeness = nodeData.metadata?.metrics?.closeness || 0;
        const isBridge = nodeData.metadata?.issues?.bridge || false;

        // Short label: class name after last dot
        const shortLabel = nodeData.id.includes('.')
            ? nodeData.id.substring(nodeData.id.lastIndexOf('.') + 1)
            : nodeData.id;

        // Node dimensions (capped at 200px for long names)
        const width = Math.min(200, Math.max(80, shortLabel.length * 7 + 24));
        const height = 28;

        // Initial position: circle layout for larger graphs, random for small
        let x: number, y: number;
        if (total > 20) {
            const angle = (2 * Math.PI * index) / total;
            const radius = Math.min(this.canvas.width, this.canvas.height) * 0.4;
            x = this.canvas.width / 2 + Math.cos(angle) * radius;
            y = this.canvas.height / 2 + Math.sin(angle) * radius;
        } else {
            x = Math.random() * this.canvas.width;
            y = Math.random() * this.canvas.height;
        }

        return {
            ...nodeData,
            x, y,
            vx: 0, vy: 0,
            radius: 12 + (betweenness * 30),
            width, height, shortLabel,
            color: this.closenessToColor(closeness),
            glow: pageRank >= this.pageRankThreshold ? 10 + (pageRank * 50) : 0,
            isBridge,
            domain: nodeData.domain || 'ungrouped'
        };
    }

    computePageRankThreshold(): number {
        const pageRanks = this.data.nodes
            .map((n: any) => n.metadata?.metrics?.pageRank || 0)
            .filter((pr: number) => pr > 0);
        if (pageRanks.length === 0) return Infinity;
        const sorted = [...pageRanks].sort((a: number, b: number) => a - b);
        return sorted[Math.floor(sorted.length * 0.8)];
    }

    closenessToColor(closeness: number): string {
        // Interpolate blue → green based on closeness
        // Low closeness (0) → blue: rgb(74, 144, 226)
        // High closeness (1) → green: rgb(74, 222, 128)
        const r = 74;
        const g = Math.floor(144 + (222 - 144) * closeness);
        const b = Math.floor(226 + (128 - 226) * closeness);
        return `rgb(${r}, ${g}, ${b})`;
    }

    darkenColor(rgb: string, amount: number): string {
        if (!rgb.startsWith('rgb(')) return rgb;
        const match = rgb.match(/\d+/g);
        if (!match) return rgb;
        const r = Math.max(0, Math.floor(parseInt(match[0]) * (1 - amount)));
        const g = Math.max(0, Math.floor(parseInt(match[1]) * (1 - amount)));
        const b = Math.max(0, Math.floor(parseInt(match[2]) * (1 - amount)));
        return `rgb(${r}, ${g}, ${b})`;
    }

    drawRoundedRect(x: number, y: number, w: number, h: number, r: number): void {
        this.ctx.beginPath();
        if ((this.ctx as any).roundRect) {
            (this.ctx as any).roundRect(x, y, w, h, r);
        } else {
            this.ctx.moveTo(x + r, y);
            this.ctx.arcTo(x + w, y, x + w, y + h, r);
            this.ctx.arcTo(x + w, y + h, x, y + h, r);
            this.ctx.arcTo(x, y + h, x, y, r);
            this.ctx.arcTo(x, y, x + w, y, r);
            this.ctx.closePath();
        }
    }

    showTooltip(node: any): void {
        const tooltip = document.getElementById('nodeTooltip');
        if (!tooltip) return;

        const nameEl = document.getElementById('tooltipName');
        const packageEl = document.getElementById('tooltipPackage');
        const iconEl = document.getElementById('tooltipIcon');
        const prEl = document.getElementById('tooltipPageRank');
        const betEl = document.getElementById('tooltipBetweenness');
        const closeEl = document.getElementById('tooltipCloseness');
        const badgesEl = document.getElementById('tooltipBadges');
        const badgeSep = document.getElementById('tooltipBadgeSeparator');
        const fanInEl = document.getElementById('tooltipFanIn');
        const fanOutEl = document.getElementById('tooltipFanOut');

        if (nameEl) nameEl.textContent = node.shortLabel || node.id;
        if (packageEl) packageEl.textContent = node.id.includes('.') ? node.id.substring(0, node.id.lastIndexOf('.')) : '';
        if (iconEl) iconEl.textContent = this.getDomainIcon(node.domain);

        const metrics = node.metadata?.metrics;
        if (prEl) prEl.textContent = metrics ? (metrics.pageRank || 0).toFixed(3) : '---';
        if (betEl) betEl.textContent = metrics ? (metrics.betweenness || 0).toFixed(3) : '---';
        if (closeEl) closeEl.textContent = metrics ? (metrics.closeness || 0).toFixed(3) : '---';

        // Badges
        if (badgesEl && badgeSep) {
            const issues = node.metadata?.issues || {};
            const badges: string[] = [];
            if (issues.hotspot) badges.push('<span class="tooltip-badge hotspot">Hotspot</span>');
            if (issues.cycle) badges.push('<span class="tooltip-badge cycle">Cycle</span>');
            if (issues.bridge || node.isBridge) badges.push('<span class="tooltip-badge bridge">Bridge</span>');
            if (issues.blindspot) badges.push('<span class="tooltip-badge blindspot">Blind Spot</span>');

            if (badges.length > 0) {
                badgesEl.innerHTML = badges.join('');
                badgesEl.style.display = '';
                badgeSep.style.display = '';
            } else {
                badgesEl.innerHTML = '';
                badgesEl.style.display = 'none';
                badgeSep.style.display = 'none';
            }
        }

        // Fan-in / fan-out
        const fanIn = this.edges.filter((e: any) => e.target === node.id).length;
        const fanOut = this.edges.filter((e: any) => e.source === node.id).length;
        if (fanInEl) fanInEl.textContent = `\u2190 ${fanIn} deps`;
        if (fanOutEl) fanOutEl.textContent = `${fanOut} deps \u2192`;

        this.updateTooltipPosition(node);
        tooltip.classList.add('visible');
        tooltip.setAttribute('aria-hidden', 'false');
        this._tooltipNode = node;
    }

    hideTooltip(): void {
        const tooltip = document.getElementById('nodeTooltip');
        if (tooltip) {
            tooltip.classList.remove('visible');
            tooltip.setAttribute('aria-hidden', 'true');
        }
        this._tooltipNode = null;
    }

    updateTooltipPosition(node: any): void {
        const tooltip = document.getElementById('nodeTooltip');
        if (!tooltip || !node) return;

        const rect = this.canvas.getBoundingClientRect();
        const screenX = node.x * this.transform.k + this.transform.x + rect.left;
        const screenY = node.y * this.transform.k + this.transform.y + rect.top;

        const arrow = document.getElementById('tooltipArrow');
        const tooltipRect = tooltip.getBoundingClientRect();
        const tw = tooltipRect.width || 280;
        const th = tooltipRect.height || 200;

        let left = screenX - tw / 2;
        let top = screenY - node.height / 2 * this.transform.k - th - 16;
        let arrowClass = 'below';

        if (top < rect.top + 10) {
            top = screenY + node.height / 2 * this.transform.k + 16;
            arrowClass = 'above';
        }

        left = Math.max(8, Math.min(window.innerWidth - tw - 8, left));

        tooltip.style.transform = `translate(${left}px, ${top}px)`;
        tooltip.style.left = '0';
        tooltip.style.top = '0';

        if (arrow) arrow.className = 'node-tooltip-arrow ' + arrowClass;
    }

    updateTooltip(): void {
        if (this._tooltipNode) {
            this.updateTooltipPosition(this._tooltipNode);
        }
    }

    getDomainIcon(domain: string): string {
        const icons: Record<string, string> = {
            core: '\u2B21', graph: '\u2B21', viz: '\u25C9',
            cli: '\u25B8', plugin: '\u25C6', service: '\u25CF',
            analysis: '\u25CE', util: '\u25CB',
            ungrouped: '\u25A1'
        };
        return icons[domain] || '\u2B21';
    }

    drawNodes(): void {
        const k = this.transform.k;
        const isFar = k < 0.5;
        const isClose = k > 1.5;
        const fadeElapsed = this.lastTime - this._fadeStartTime;
        const fadeIn = this.reducedMotion ? 1 : Math.min(1, fadeElapsed / 500);

        for (const node of this.nodes) {
            if (this.viewLevel === 'CLASS' && node.domain !== this.expandedDomain) {
                this.ctx.globalAlpha = 0.3 * fadeIn;
            } else {
                this.ctx.globalAlpha = fadeIn;
            }

            if (isFar) {
                this.ctx.beginPath();
                this.ctx.arc(node.x, node.y, 3, 0, Math.PI * 2);
                this.ctx.fillStyle = node.color;
                this.ctx.fill();
                this.ctx.globalAlpha = 1;
                continue;
            }

            // Draw rounded rect with gradient
            const hw = node.width / 2;
            const hh = node.height / 2;

            if (node.glow > 0) {
                this.ctx.shadowBlur = node.glow;
                this.ctx.shadowColor = node.color;
            } else {
                this.ctx.shadowBlur = 0;
            }

            this.drawRoundedRect(node.x - hw, node.y - hh, node.width, node.height, 4);
            this.ctx.fillStyle = node.color;
            this.ctx.fill();
            this.ctx.strokeStyle = 'rgba(255, 255, 255, 0.10)';
            this.ctx.lineWidth = 1;
            this.ctx.stroke();

            this.ctx.shadowBlur = 0;
            this.ctx.fillStyle = '#fafafa';
            this.ctx.font = '11px Geist Mono';
            this.ctx.textAlign = 'center';
            this.ctx.textBaseline = 'middle';
            this.ctx.fillText(node.shortLabel, node.x, node.y, node.width - 8);

            if (isClose) {
                const betweenness = node.metadata?.metrics?.betweenness || 0;
                const barWidth = node.width * Math.min(1, betweenness);
                if (barWidth > 0) {
                    this.ctx.fillStyle = '#4ade80';
                    this.ctx.fillRect(node.x - hw, node.y + hh - 2, barWidth, 2);
                }
            }

            this.ctx.globalAlpha = 1;
        }
    }

    render(timestamp?: number): void {
        if (timestamp) {
            this.lastTime = timestamp;
            if (!this._fadeStartTime) this._fadeStartTime = timestamp;
        }

        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        if (!this.reducedMotion) {
            this.simulation.update();
        }

        this.ctx.save();
        this.ctx.translate(this.transform.x, this.transform.y);
        this.ctx.scale(this.transform.k, this.transform.k);

        this.drawEdges();
        this.drawNodes();

        this.ctx.restore();

        this.updateTooltip();
    }

    private drawEdges(): void {
        // Placeholder
    }

    setNodePosition(nodeId: string, x: number, y: number): void {
        this.simulation.setNodePosition(nodeId, x, y);
    }

    expandDomain(domainId: string): void {
        const domainNode = this.nodeMap.get(domainId) || this.nodes.find((n: any) => n.domain === domainId);
        if (!domainNode) return;

        this.viewLevel = 'CLASS';
        this.expandedDomain = domainId;
        this.simulation.restart();
    }

    returnToDomainView(): void {
        this.viewLevel = 'DOMAIN';
        this.expandedDomain = null;
        this.simulation.restart();
    }
}
