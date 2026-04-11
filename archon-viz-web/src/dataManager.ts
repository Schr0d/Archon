/**
 * DataManager: Handles loading, validation, and error handling for graph data
 */
export class DataManager {
    private data: any = null;
    private readonly schemaVersion = "archon-metadata-v1";

    constructor() {
        // Schema version is initialized as readonly property
    }

    async load(): Promise<any> {
        // Try static export first (window.GRAPH_DATA), then fetch
        try {
            const rawData = window.GRAPH_DATA || await this.fetchFromServer();
            this.validateSchema(rawData);
            this.data = this.processData(rawData);
            return this.data;
        } catch (e) {
            this.showError((e as Error).message);
            throw e;
        }
    }

    private async fetchFromServer(): Promise<any> {
        const response = await fetch('/api/graph');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        return await response.json();
    }

    private validateSchema(data: any): void {
        if (!data.$schema || !data.$schema.startsWith(this.schemaVersion)) {
            throw new Error(`Unsupported schema version: ${data.$schema}`);
        }
        if (!data.nodes || !Array.isArray(data.nodes)) {
            throw new Error('Missing or invalid "nodes" field');
        }
        if (!data.edges || !Array.isArray(data.edges)) {
            throw new Error('Missing or invalid "edges" field');
        }
    }

    processData(data: any): any {
        // Check for Tier 3 metrics
        const hasFullAnalysis = !!data.fullAnalysis;
        if (!hasFullAnalysis) {
            this.showWarning('Tier 3 metrics unavailable, using Tier 1 fallback');
        }
        return { ...data, hasFullAnalysis };
    }

    private showError(message: string): void {
        const banner = document.getElementById('errorBanner');
        if (banner) {
            banner.textContent = `Error: ${message}`;
            banner.style.display = 'flex';
        }
    }

    private showWarning(message: string): void {
        const banner = document.getElementById('warningBanner');
        if (banner) {
            banner.textContent = `Warning: ${message}`;
            banner.style.display = 'flex';
        }
    }
}
