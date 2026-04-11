import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['**/*.{test,spec}.{js,ts}', '**/__tests__/**/*.{js,ts}'],
  },
});
