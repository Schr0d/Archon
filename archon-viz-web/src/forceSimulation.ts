/**
 * Quadtree node for Barnes-Hut optimization (O(n log n) repulsion)
 */
class QuadTreeNode {
    bounds: { x: number; y: number; width: number; height: number };
    maxCapacity: number;
    maxDepth: number;
    nodes: any[] = [];
    children: QuadTreeNode[] | null = null;
    centerOfMass: { x: number; y: number; mass: number } = { x: 0, y: 0, mass: 0 };
    depth: number;

    constructor(
        bounds: { x: number; y: number; width: number; height: number },
        maxCapacity = 10,
        maxDepth = 8,
        depth = 0
    ) {
        this.bounds = bounds;
        this.maxCapacity = maxCapacity;
        this.maxDepth = maxDepth;
        this.depth = depth;
    }

    insert(node: any): void {
        if (this.children) {
            this.insertIntoChild(node);
            this.updateCenterOfMass(node);
            return;
        }

        this.nodes.push(node);
        this.updateCenterOfMass(node);

        if (this.nodes.length > this.maxCapacity && this.depth < this.maxDepth) {
            this.split();
        }
    }

    private split(): void {
        const { x, y, width, height } = this.bounds;
        const hw = width / 2;
        const hh = height / 2;
        this.children = [
            new QuadTreeNode({ x, y, width: hw, height: hh }, this.maxCapacity, this.maxDepth, this.depth + 1),
            new QuadTreeNode({ x: x + hw, y, width: hw, height: hh }, this.maxCapacity, this.maxDepth, this.depth + 1),
            new QuadTreeNode({ x, y: y + hh, width: hw, height: hh }, this.maxCapacity, this.maxDepth, this.depth + 1),
            new QuadTreeNode({ x: x + hw, y: y + hh, width: hw, height: hh }, this.maxCapacity, this.maxDepth, this.depth + 1)
        ];

        for (const node of this.nodes) {
            this.insertIntoChild(node);
        }
        this.nodes = [];
    }

    private insertIntoChild(node: any): void {
        for (const child of this.children!) {
            if (this.contains(child.bounds, node)) {
                child.insert(node);
                return;
            }
        }
    }

    private contains(bounds: { x: number; y: number; width: number; height: number }, node: any): boolean {
        return node.x >= bounds.x && node.x < bounds.x + bounds.width &&
               node.y >= bounds.y && node.y < bounds.y + bounds.height;
    }

    private updateCenterOfMass(node: any): void {
        const totalMass = this.centerOfMass.mass + node.mass || 1;
        this.centerOfMass.x = (this.centerOfMass.x * this.centerOfMass.mass + node.x * (node.mass || 1)) / totalMass;
        this.centerOfMass.y = (this.centerOfMass.y * this.centerOfMass.mass + node.y * (node.mass || 1)) / totalMass;
        this.centerOfMass.mass = totalMass;
    }
}

/**
 * Force-directed physics simulation with Verlet integration
 */
export class ForceSimulation {
    nodes: any[];
    edges: any[];
    private nodeCount: number;

    // Physics constants
    private readonly REPULSION_STRENGTH = 1000;
    private readonly IDEAL_EDGE_LENGTH = 150;
    private readonly SPRING_CONSTANT = 0.05;
    private readonly DAMPING = 0.9;
    private readonly COOLING_RATE = 0.95;
    private readonly MIN_TEMPERATURE = 0.01;
    private readonly MAX_ITERATIONS = 500;

    temperature: number;
    active: boolean;
    iteration: number;
    useBarnesHut: boolean;

    constructor(nodes: any[], edges: any[]) {
        this.nodes = nodes.map(n => ({ ...n, vx: 0, vy: 0, fx: 0, fy: 0, mass: 1 }));
        this.edges = edges;
        this.nodeCount = nodes.length;

        this.temperature = 1.0;
        this.active = true;
        this.iteration = 0;

        // Choose algorithm based on node count
        this.useBarnesHut = this.nodeCount >= 250;
    }

    update(): boolean {
        if (!this.active) return false;

        // Clear forces
        for (const node of this.nodes) {
            node.fx = 0;
            node.fy = 0;
        }

        if (this.useBarnesHut) {
            this.applyBarnesHutRepulsion();
        } else {
            this.applyDirectRepulsion();
        }

        this.applyAttraction();
        this.applyForces();
        this.cool();

        return this.active;
    }

    private applyDirectRepulsion(): void {
        // O(n²) repulsion for small graphs
        for (let i = 0; i < this.nodes.length; i++) {
            for (let j = i + 1; j < this.nodes.length; j++) {
                const a = this.nodes[i];
                const b = this.nodes[j];
                const dx = b.x - a.x;
                const dy = b.y - a.y;
                const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                const force = this.REPULSION_STRENGTH / (dist * dist);
                const fx = (dx / dist) * force;
                const fy = (dy / dist) * force;
                a.fx -= fx;
                a.fy -= fy;
                b.fx += fx;
                b.fy += fy;
            }
        }
    }

    private applyBarnesHutRepulsion(): void {
        const bounds = this.computeBounds();
        const quadtree = new QuadTreeNode(bounds);
        for (const node of this.nodes) {
            quadtree.insert(node);
        }

        for (const node of this.nodes) {
            this.applyRepulsionFromQuadtree(node, quadtree, bounds);
        }
    }

    private applyRepulsionFromQuadtree(node: any, quadtree: QuadTreeNode, bounds: any, theta = 0.5): void {
        if (quadtree.nodes.length === 0 && !quadtree.children) return;

        const dx = quadtree.centerOfMass.x - node.x;
        const dy = quadtree.centerOfMass.y - node.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const width = bounds.width;

        // If far enough or leaf node, treat as single mass
        if (width / dist < theta || quadtree.nodes.length > 0) {
            const force = this.REPULSION_STRENGTH * quadtree.centerOfMass.mass / (dist * dist);
            node.fx -= (dx / dist) * force;
            node.fy -= (dy / dist) * force;
            return;
        }

        // Otherwise, recurse into children
        if (quadtree.children) {
            for (const child of quadtree.children) {
                this.applyRepulsionFromQuadtree(node, child, child.bounds, theta);
            }
        }
    }

    private computeBounds(): { x: number; y: number; width: number; height: number } {
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        for (const node of this.nodes) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x);
            maxY = Math.max(maxY, node.y);
        }
        return {
            x: minX - 100,
            y: minY - 100,
            width: (maxX - minX) + 200,
            height: (maxY - minY) + 200
        };
    }

    private applyAttraction(): void {
        // O(e) spring attraction
        for (const edge of this.edges) {
            const source = this.nodes.find(n => n.id === edge.source);
            const target = this.nodes.find(n => n.id === edge.target);
            if (!source || !target) continue;

            const dx = target.x - source.x;
            const dy = target.y - source.y;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const force = (dist - this.IDEAL_EDGE_LENGTH) * this.SPRING_CONSTANT;
            const fx = (dx / dist) * force;
            const fy = (dy / dist) * force;

            source.fx += fx;
            source.fy += fy;
            target.fx -= fx;
            target.fy -= fy;
        }
    }

    private applyForces(): void {
        // Verlet integration with damping
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
        const node = this.nodes.find(n => n.id === nodeId);
        if (node) {
            node.x = x;
            node.y = y;
            node.vx = 0;
            node.vy = 0;
        }
    }
}
