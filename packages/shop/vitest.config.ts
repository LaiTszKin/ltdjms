import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    testTimeout: 30000,
    setupFiles: ['./vitest.setup.ts'],
    globalSetup: ['../shared/src/__tests__/vitest.globalSetup.ts'],
    globalTeardown: ['../shared/src/__tests__/vitest.globalTeardown.ts'],
    fileParallelism: false,
  },
});
