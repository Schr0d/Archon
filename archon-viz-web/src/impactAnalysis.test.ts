import { describe, it, expect } from 'vitest';
import { computeImpact } from './impactAnalysis';

describe('computeImpact', () => {
  it('returns empty sets for empty graph', () => {
    const result = computeImpact([], 'source');
    expect(result.p0.size).toBe(0);
    expect(result.p1.size).toBe(0);
    expect(result.p2.size).toBe(0);
  });

  it('returns empty sets when source is not in graph', () => {
    const edges = [{ source: 'a', target: 'b' }];
    const result = computeImpact(edges, 'unknown');
    expect(result.p0.size).toBe(0);
    expect(result.p1.size).toBe(0);
    expect(result.p2.size).toBe(0);
  });

  it('classifies single direct dependency as P0', () => {
    const edges = [{ source: 'src', target: 'A' }];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A']));
    expect(result.p1.size).toBe(0);
    expect(result.p2.size).toBe(0);
  });

  it('handles bidirectional edge (same node via both directions)', () => {
    // A -> src AND src -> A  means A is 1 hop away in the undirected view
    const edges = [
      { source: 'A', target: 'src' },
      { source: 'src', target: 'A' }
    ];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A']));
    expect(result.p1.size).toBe(0);
    expect(result.p2.size).toBe(0);
  });

  it('classifies two-hop chain as P0 and P1', () => {
    // src -> A -> B
    const edges = [
      { source: 'src', target: 'A' },
      { source: 'A', target: 'B' }
    ];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A']));
    expect(result.p1).toEqual(new Set(['B']));
    expect(result.p2.size).toBe(0);
  });

  it('classifies three-hop chain as P0, P1, P2', () => {
    // src -> A -> B -> C
    const edges = [
      { source: 'src', target: 'A' },
      { source: 'A', target: 'B' },
      { source: 'B', target: 'C' }
    ];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A']));
    expect(result.p1).toEqual(new Set(['B']));
    expect(result.p2).toEqual(new Set(['C']));
  });

  it('handles diamond graph correctly', () => {
    // src -> A, src -> B, A -> C, B -> C
    const edges = [
      { source: 'src', target: 'A' },
      { source: 'src', target: 'B' },
      { source: 'A', target: 'C' },
      { source: 'B', target: 'C' }
    ];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A', 'B']));
    expect(result.p1).toEqual(new Set(['C']));
    expect(result.p2.size).toBe(0);
  });

  it('splits multiple paths into correct P0/P1/P2', () => {
    // src -> A -> B -> C  AND  src -> D -> E -> F
    const edges = [
      { source: 'src', target: 'A' },
      { source: 'A', target: 'B' },
      { source: 'B', target: 'C' },
      { source: 'src', target: 'D' },
      { source: 'D', target: 'E' },
      { source: 'E', target: 'F' }
    ];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A', 'D']));
    expect(result.p1).toEqual(new Set(['B', 'E']));
    expect(result.p2).toEqual(new Set(['C', 'F']));
  });

  it('follows incoming edges (reverse direction)', () => {
    // A -> src  (A depends on src)
    const edges = [{ source: 'A', target: 'src' }];
    const result = computeImpact(edges, 'src');
    expect(result.p0).toEqual(new Set(['A']));
    expect(result.p1.size).toBe(0);
    expect(result.p2.size).toBe(0);
  });

  it('completes 90-node graph in under 1ms', () => {
    // Build a linear chain of 90 nodes: src -> n0 -> n1 -> ... -> n88
    const edges: Array<{ source: string; target: string }> = [];
    edges.push({ source: 'src', target: 'n0' });
    for (let i = 0; i < 88; i++) {
      edges.push({ source: `n${i}`, target: `n${i + 1}` });
    }

    const start = performance.now();
    const result = computeImpact(edges, 'src');
    const elapsed = performance.now() - start;

    expect(elapsed).toBeLessThan(1);
    expect(result.p0.size).toBe(1);
    expect(result.p1.size).toBe(1);
    expect(result.p2.size).toBe(87);
  });
});
