/**
 * Impact analysis via BFS propagation.
 *
 * Given a directed dependency graph and a source node, computes three
 * concentric blast-radius sets (P0/P1/P2) representing how far a change
 * to the source would propagate through the graph.
 *
 * BFS follows edges in BOTH directions -- outgoing (dependencies the
 * source relies on) and incoming (dependents that rely on the source).
 */

export interface ImpactResult {
  p0: Set<string>;  // Direct dependencies — exactly 1 hop
  p1: Set<string>;  // Transitive — exactly 2 hops
  p2: Set<string>;  // Deep — 3+ hops
}

/**
 * Compute impact propagation levels from a source node.
 *
 * @param edges  Directed edges in the dependency graph ({source, target})
 * @param sourceNodeId  The node whose blast radius to compute
 * @returns P0/P1/P2 sets of affected node IDs (source excluded)
 */
export function computeImpact(
  edges: Array<{ source: string; target: string }>,
  sourceNodeId: string
): ImpactResult {
  const p0 = new Set<string>();
  const p1 = new Set<string>();
  const p2 = new Set<string>();

  // Build undirected adjacency list (follow edges in both directions)
  const adjacency = new Map<string, string[]>();
  for (const edge of edges) {
    let neighbors = adjacency.get(edge.source);
    if (!neighbors) {
      neighbors = [];
      adjacency.set(edge.source, neighbors);
    }
    neighbors.push(edge.target);

    let reverseNeighbors = adjacency.get(edge.target);
    if (!reverseNeighbors) {
      reverseNeighbors = [];
      adjacency.set(edge.target, reverseNeighbors);
    }
    reverseNeighbors.push(edge.source);
  }

  // If source is not in the graph, return empty sets
  if (!adjacency.has(sourceNodeId)) {
    return { p0, p1, p2 };
  }

  // BFS from source, tracking hop distance
  const visited = new Set<string>([sourceNodeId]);
  const queue: Array<{ id: string; distance: number }> = [
    { id: sourceNodeId, distance: 0 }
  ];

  while (queue.length > 0) {
    const current = queue.shift()!;
    const neighbors = adjacency.get(current.id) || [];

    for (const neighbor of neighbors) {
      if (visited.has(neighbor)) continue;
      visited.add(neighbor);

      const distance = current.distance + 1;

      if (distance === 1) {
        p0.add(neighbor);
      } else if (distance === 2) {
        p1.add(neighbor);
      } else {
        p2.add(neighbor);
      }

      queue.push({ id: neighbor, distance });
    }
  }

  return { p0, p1, p2 };
}
