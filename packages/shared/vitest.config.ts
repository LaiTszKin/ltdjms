import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    testTimeout: 30000,
    globalSetup: ['./src/__tests__/vitest.globalSetup.ts'],
    globalTeardown: ['./src/__tests__/vitest.globalTeardown.ts'],
  },
});
