import { describe, it, expect, beforeEach, vi } from 'vitest';
import { DataManager } from './dataManager';

describe('DataManager', () => {
  beforeEach(() => {
    // Reset global state before each test
    if (typeof window !== 'undefined') {
      delete (window as any).GRAPH_DATA;
    }
  });

  describe('constructor', () => {
    it('creates instance with schema version', () => {
      const manager = new DataManager();
      expect(manager.schemaVersion).toBe('archon-metadata-v1');
    });
  });

  describe('load', () => {
    it('loads data from window.GRAPH_DATA when available', async () => {
      const mockData = {
        $schema: 'archon-metadata-v1',
        nodes: [{ id: 'test-node' }],
        edges: [],
      };

      (window as any).GRAPH_DATA = mockData;

      const manager = new DataManager();
      const data = await manager.load();

      expect(data).toMatchObject(mockData);
      expect(data.hasFullAnalysis).toBe(false);
    });

    it('fetches from /api/graph when window.GRAPH_DATA not available', async () => {
      // This will fail because we don't have fetch mocked yet - that's expected
      const manager = new DataManager();

      // Should attempt to fetch from /api/graph
      await expect(manager.load()).rejects.toThrow();
    });

    it('throws error when schema version is unsupported', async () => {
      (window as any).GRAPH_DATA = {
        $schema: 'wrong-version',
        nodes: [{ id: 'test-node' }],
        edges: [],
      };

      const manager = new DataManager();

      await expect(manager.load()).rejects.toThrow('Unsupported schema version: wrong-version');
    });

    it('throws error when nodes field is missing', async () => {
      (window as any).GRAPH_DATA = {
        $schema: 'archon-metadata-v1',
        // nodes missing
        edges: [],
      };

      const manager = new DataManager();

      await expect(manager.load()).rejects.toThrow('Missing or invalid "nodes" field');
    });

    it('throws error when edges field is missing', async () => {
      (window as any).GRAPH_DATA = {
        $schema: 'archon-metadata-v1',
        nodes: [{ id: 'test-node' }],
        // edges missing
      };

      const manager = new DataManager();

      await expect(manager.load()).rejects.toThrow('Missing or invalid "edges" field');
    });
  });

  describe('processData', () => {
    it('marks as having full analysis when fullAnalysis present', () => {
      const manager = new DataManager();
      const inputData = {
        $schema: 'archon-metadata-v1',
        nodes: [{ id: 'test-node' }],
        edges: [],
        fullAnalysis: { pageRank: {} },
      };

      const result = manager.processData(inputData);

      expect(result.hasFullAnalysis).toBe(true);
    });

    it('marks as lacking full analysis when fullAnalysis missing', () => {
      const manager = new DataManager();
      const inputData = {
        $schema: 'archon-metadata-v1',
        nodes: [{ id: 'test-node' }],
        edges: [],
      };

      const result = manager.processData(inputData);

      expect(result.hasFullAnalysis).toBe(false);
    });
  });
});
