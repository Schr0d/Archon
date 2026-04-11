import { describe, it, expect, beforeEach } from 'vitest';
import { ForceSimulation } from './forceSimulation';

describe('ForceSimulation', () => {
  describe('constructor', () => {
    it('creates simulation with nodes and edges', () => {
      const nodes = [
        { id: 'a', x: 100, y: 100 },
        { id: 'b', x: 200, y: 150 }
      ];
      const edges = [{ source: 'a', target: 'b' }];

      const sim = new ForceSimulation(nodes, edges);

      expect(sim.nodes).toHaveLength(2);
      expect(sim.edges).toEqual(edges);
    });

    it('initializes nodes with velocity and force properties', () => {
      const nodes = [{ id: 'a', x: 100, y: 100 }];
      const sim = new ForceSimulation(nodes, []);

      expect(sim.nodes[0].vx).toBe(0);
      expect(sim.nodes[0].vy).toBe(0);
      expect(sim.nodes[0].fx).toBe(0);
      expect(sim.nodes[0].fy).toBe(0);
    });

    it('uses Barnes-Hut for 250+ nodes', () => {
      const nodes = Array.from({ length: 250 }, (_, i) => ({
        id: `n${i}`,
        x: Math.random() * 800,
        y: Math.random() * 600
      }));
      const sim = new ForceSimulation(nodes, []);

      expect(sim['useBarnesHut']).toBe(true);
    });

    it('uses direct repulsion for <250 nodes', () => {
      const nodes = Array.from({ length: 100 }, (_, i) => ({
        id: `n${i}`,
        x: Math.random() * 800,
        y: Math.random() * 600
      }));
      const sim = new ForceSimulation(nodes, []);

      expect(sim['useBarnesHut']).toBe(false);
    });
  });

  describe('update', () => {
    it('returns true while simulation is active', () => {
      const nodes = [{ id: 'a', x: 100, y: 100 }];
      const sim = new ForceSimulation(nodes, []);

      expect(sim.update()).toBe(true);
    });

    it('returns false when temperature drops below minimum', () => {
      const nodes = [{ id: 'a', x: 100, y: 100 }];
      const sim = new ForceSimulation(nodes, []);

      // Run until settled
      let iterations = 0;
      while (sim.update() && iterations < 600) {
        iterations++;
      }

      expect(sim.update()).toBe(false);
    });

    it('applies repulsion forces between nodes', () => {
      const nodes = [
        { id: 'a', x: 100, y: 100 },
        { id: 'b', x: 110, y: 100 }
      ];
      const sim = new ForceSimulation(nodes, []);

      const initialDistance = Math.abs(sim.nodes[0].x - sim.nodes[1].x);
      sim.update();
      const finalDistance = Math.abs(sim.nodes[0].x - sim.nodes[1].x);

      expect(finalDistance).toBeGreaterThan(initialDistance);
    });

    it('applies attraction forces along edges', () => {
      const nodes = [
        { id: 'a', x: 100, y: 100 },
        { id: 'b', x: 300, y: 100 }
      ];
      const edges = [{ source: 'a', target: 'b' }];
      const sim = new ForceSimulation(nodes, edges);

      const initialDistance = 300 - 100;
      sim.update();
      const finalDistance = Math.abs(sim.nodes[1].x - sim.nodes[0].x);

      // Attraction should pull nodes together
      expect(finalDistance).toBeLessThan(initialDistance);
    });
  });

  describe('setNodePosition', () => {
    it('sets node position and resets velocity', () => {
      const nodes = [{ id: 'a', x: 100, y: 100, vx: 5, vy: 3 }];
      const sim = new ForceSimulation(nodes, []);

      sim.setNodePosition('a', 500, 300);

      expect(sim.nodes[0].x).toBe(500);
      expect(sim.nodes[0].y).toBe(300);
      expect(sim.nodes[0].vx).toBe(0);
      expect(sim.nodes[0].vy).toBe(0);
    });

    it('does nothing if node id not found', () => {
      const nodes = [{ id: 'a', x: 100, y: 100 }];
      const sim = new ForceSimulation(nodes, []);

      sim.setNodePosition('nonexistent', 500, 300);

      expect(nodes[0].x).toBe(100);
      expect(nodes[0].y).toBe(100);
    });
  });

  describe('restart', () => {
    it('resets temperature and iteration count', () => {
      const nodes = [{ id: 'a', x: 100, y: 100 }];
      const sim = new ForceSimulation(nodes, []);

      // Run until settled
      while (sim.update()) {}

      sim.restart();

      expect(sim['temperature']).toBe(1.0);
      expect(sim['iteration']).toBe(0);
      expect(sim.update()).toBe(true);
    });
  });
});
