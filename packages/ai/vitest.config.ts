import { defineConfig } from 'vitest/config';
import path from 'node:path';

const sharedTestSetup = path.resolve(__dirname, '../shared/src/__tests__/vitest.globalSetup.ts');
const sharedTestTeardown = path.resolve(__dirname, '../shared/src/__tests__/vitest.globalTeardown.ts');

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    testTimeout: 30000,
    setupFiles: ['./vitest.setup.ts'],
    globalSetup: [sharedTestSetup],
    globalTeardown: [sharedTestTeardown],
  },
});
