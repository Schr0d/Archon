/**
 * arrowRouter.ts — L-shape polyline routing between positioned rectangular nodes.
 *
 * Pure function module used for SVG arrow rendering in Mode 1 (domain overview)
 * and Mode 2 (boundary drill-down) views.
 */

export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export type Edge = 'top' | 'right' | 'bottom' | 'left';

export interface RouteResult {
  /** SVG polyline points attribute, e.g. "100,50 200,50 200,150" */
  points: string;
  exitEdge: Edge;
  enterEdge: Edge;
}

const OPPOSITE: Record<Edge, Edge> = {
  top: 'bottom',
  bottom: 'top',
  left: 'right',
  right: 'left',
};

/**
 * Returns the center point of a given edge of a rectangle.
 */
export function getEdgeCenter(rect: Rect, edge: Edge): { x: number; y: number } {
  switch (edge) {
    case 'top':
      return { x: rect.x + rect.width / 2, y: rect.y };
    case 'right':
      return { x: rect.x + rect.width, y: rect.y + rect.height / 2 };
    case 'bottom':
      return { x: rect.x + rect.width / 2, y: rect.y + rect.height };
    case 'left':
      return { x: rect.x, y: rect.y + rect.height / 2 };
  }
}

/**
 * Determines which edge of the source node the arrow should exit from,
 * based on the relative position of the target node.
 *
 * Priority: horizontal edges win when |dx| >= |dy| (tie goes horizontal).
 */
export function determineExitEdge(source: Rect, target: Rect): Edge {
  const sourceCx = source.x + source.width / 2;
  const sourceCy = source.y + source.height / 2;
  const targetCx = target.x + target.width / 2;
  const targetCy = target.y + target.height / 2;

  const dx = targetCx - sourceCx;
  const dy = targetCy - sourceCy;

  if (dx > 0 && Math.abs(dx) >= Math.abs(dy)) return 'right';
  if (dx < 0 && Math.abs(dx) >= Math.abs(dy)) return 'left';
  if (dy > 0) return 'bottom';
  return 'top';
}

/**
 * Routes an L-shape polyline from source to target rectangle.
 *
 * Returns empty points string if source and target are the same node.
 */
export function routeArrow(source: Rect, target: Rect): RouteResult {
  // Guard: same node
  if (
    source.x === target.x &&
    source.y === target.y &&
    source.width === target.width &&
    source.height === target.height
  ) {
    return { points: '', exitEdge: 'right', enterEdge: 'left' };
  }

  const exitEdge = determineExitEdge(source, target);
  const enterEdge = OPPOSITE[exitEdge];

  const start = getEdgeCenter(source, exitEdge);
  const end = getEdgeCenter(target, enterEdge);

  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const horizontalFirst = Math.abs(dx) >= Math.abs(dy);

  let points: string;

  if (horizontalFirst) {
    // Horizontal segment first: start → (endX, startY) → end
    points = `${start.x},${start.y} ${end.x},${start.y} ${end.x},${end.y}`;
  } else {
    // Vertical segment first: start → (startX, endY) → end
    points = `${start.x},${start.y} ${start.x},${end.y} ${end.x},${end.y}`;
  }

  return { points, exitEdge, enterEdge };
}
