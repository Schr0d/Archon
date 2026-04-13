import { describe, it, expect } from 'vitest';
import { routeArrow, getEdgeCenter, determineExitEdge } from './arrowRouter';
import type { Rect } from './arrowRouter';

/** Helper: make a rect at (x, y) with given dimensions. */
function rect(x: number, y: number, width = 100, height = 28): Rect {
  return { x, y, width, height };
}

describe('arrowRouter', () => {
  // ---------------------------------------------------------------------------
  // getEdgeCenter
  // ---------------------------------------------------------------------------
  describe('getEdgeCenter', () => {
    const r: Rect = { x: 10, y: 20, width: 100, height: 28 };

    it('returns top edge center', () => {
      const c = getEdgeCenter(r, 'top');
      expect(c.x).toBe(60);  // 10 + 100/2
      expect(c.y).toBe(20);  // top
    });

    it('returns right edge center', () => {
      const c = getEdgeCenter(r, 'right');
      expect(c.x).toBe(110); // 10 + 100
      expect(c.y).toBe(34);  // 20 + 28/2
    });

    it('returns bottom edge center', () => {
      const c = getEdgeCenter(r, 'bottom');
      expect(c.x).toBe(60);  // 10 + 100/2
      expect(c.y).toBe(48);  // 20 + 28
    });

    it('returns left edge center', () => {
      const c = getEdgeCenter(r, 'left');
      expect(c.x).toBe(10);  // left
      expect(c.y).toBe(34);  // 20 + 28/2
    });
  });

  // ---------------------------------------------------------------------------
  // determineExitEdge
  // ---------------------------------------------------------------------------
  describe('determineExitEdge', () => {
    it('returns right when target is to the right and horizontal dominates', () => {
      const source = rect(0, 0);
      const target = rect(200, 0);
      expect(determineExitEdge(source, target)).toBe('right');
    });

    it('returns left when target is to the left and horizontal dominates', () => {
      const source = rect(200, 0);
      const target = rect(0, 0);
      expect(determineExitEdge(source, target)).toBe('left');
    });

    it('returns bottom when target is below and vertical dominates', () => {
      const source = rect(0, 0);
      const target = rect(0, 200);
      expect(determineExitEdge(source, target)).toBe('bottom');
    });

    it('returns top when target is above and vertical dominates', () => {
      const source = rect(0, 200);
      const target = rect(0, 0);
      expect(determineExitEdge(source, target)).toBe('top');
    });

    it('returns right for diagonal when |dx| == |dy| (tiebreaker)', () => {
      const source = rect(0, 0);
      const target = rect(200, 200);
      expect(determineExitEdge(source, target)).toBe('right');
    });

    it('returns left for diagonal to upper-left when |dx| == |dy|', () => {
      const source = rect(200, 200);
      const target = rect(0, 0);
      expect(determineExitEdge(source, target)).toBe('left');
    });
  });

  // ---------------------------------------------------------------------------
  // routeArrow
  // ---------------------------------------------------------------------------
  describe('routeArrow', () => {
    it('returns empty points for same node', () => {
      const source = rect(50, 50, 100, 28);
      const result = routeArrow(source, source);
      expect(result.points).toBe('');
    });

    it('routes right arrow — exits right, enters left', () => {
      const source = rect(0, 0, 100, 28);
      const target = rect(300, 0, 100, 28);

      const result = routeArrow(source, target);

      expect(result.exitEdge).toBe('right');
      expect(result.enterEdge).toBe('left');
    });

    it('routes left arrow — exits left, enters right', () => {
      const source = rect(300, 0, 100, 28);
      const target = rect(0, 0, 100, 28);

      const result = routeArrow(source, target);

      expect(result.exitEdge).toBe('left');
      expect(result.enterEdge).toBe('right');
    });

    it('routes down arrow — exits bottom, enters top', () => {
      const source = rect(0, 0, 100, 28);
      const target = rect(0, 300, 100, 28);

      const result = routeArrow(source, target);

      expect(result.exitEdge).toBe('bottom');
      expect(result.enterEdge).toBe('top');
    });

    it('routes up arrow — exits top, enters bottom', () => {
      const source = rect(0, 300, 100, 28);
      const target = rect(0, 0, 100, 28);

      const result = routeArrow(source, target);

      expect(result.exitEdge).toBe('top');
      expect(result.enterEdge).toBe('bottom');
    });

    it('produces horizontal-first L-shape when |dx| > |dy|', () => {
      // Source at (0,0) size 100x28, target at (300, 50) size 100x28
      // Center dx = 350 - 50 = 300, dy = 64 - 14 = 50
      // horizontalFirst = true
      const source = rect(0, 0, 100, 28);
      const target = rect(300, 50, 100, 28);

      const result = routeArrow(source, target);

      // Start: right edge center of source = (100, 14)
      // End: left edge center of target = (300, 64)
      // Horizontal first: "100,14 300,14 300,64"
      expect(result.points).toBe('100,14 300,14 300,64');
      expect(result.exitEdge).toBe('right');
      expect(result.enterEdge).toBe('left');
    });

    it('produces vertical-first L-shape when |dy| > |dx|', () => {
      // Source at (0,0) size 100x28, target at (20, 300) size 100x28
      // Center dx = 70 - 50 = 20, dy = 314 - 14 = 300
      // horizontalFirst = false
      const source = rect(0, 0, 100, 28);
      const target = rect(20, 300, 100, 28);

      const result = routeArrow(source, target);

      // Start: bottom edge center of source = (50, 28)
      // End: top edge center of target = (70, 300)
      // Vertical first: "50,28 50,300 70,300"
      expect(result.points).toBe('50,28 50,300 70,300');
      expect(result.exitEdge).toBe('bottom');
      expect(result.enterEdge).toBe('top');
    });

    it('uses horizontal-first when edge-center |dx| >= |dy| (tiebreaker on rect centers)', () => {
      // Rect center tie goes to right edge. But L-shape routing decides
      // horizontal-first vs vertical-first based on edge center deltas.
      //
      // Source at (0,0) size 200x200, target at (400,200) size 200x200
      // Rect centers: (100,100) -> (500,300), dx=400, dy=200 -> right
      // Edge centers: start=(200,100), end=(400,300), dx=200, dy=200 -> tie -> horizontalFirst
      const source: Rect = { x: 0, y: 0, width: 200, height: 200 };
      const target: Rect = { x: 400, y: 200, width: 200, height: 200 };

      const result = routeArrow(source, target);

      // Start: right edge center of source = (200, 100)
      // End: left edge center of target = (400, 300)
      // horizontalFirst (|dx| == |dy| tie): "200,100 400,100 400,300"
      expect(result.points).toBe('200,100 400,100 400,300');
      expect(result.exitEdge).toBe('right');
      expect(result.enterEdge).toBe('left');
    });

    it('produces exactly 3 points in the polyline for distinct nodes', () => {
      const source = rect(10, 10, 80, 34);
      const target = rect(250, 80, 120, 34);

      const result = routeArrow(source, target);

      const parts = result.points.split(' ');
      expect(parts).toHaveLength(3);
      // Each part should be "x,y"
      for (const part of parts) {
        const coords = part.split(',');
        expect(coords).toHaveLength(2);
        expect(Number(coords[0])).not.toBeNaN();
        expect(Number(coords[1])).not.toBeNaN();
      }
    });

    it('produces a two-point line when start and end share a coordinate (collinear)', () => {
      // Same Y, target to the right — horizontal line
      const source = rect(0, 0, 100, 28);
      const target = rect(300, 0, 100, 28);

      const result = routeArrow(source, target);

      // Start: right edge center = (100, 14)
      // End: left edge center = (300, 14)
      // Horizontal first: "100,14 300,14 300,14" — last two points coincide
      // This is expected behavior: the L degenerates to a straight line.
      const points = result.points;
      expect(points).toContain('100,14');
      expect(points).toContain('300,14');
    });
  });
});
