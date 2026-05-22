import { defineConfig } from 'vitest/config';
import path from 'path';

const sharedSrc = path.resolve(__dirname, '../shared/src');

export default defineConfig({
  resolve: {
    alias: [
      { find: /^@ltdjms\/shared\/(.+)$/, replacement: sharedSrc + '/$1' },
      { find: /^@ltdjms\/shared$/, replacement: sharedSrc },
    ],
  },
  test: {
    include: ['src/**/*.test.ts'],
    testTimeout: 30000,
    fileParallelism: false,
    pool: 'forks',
    setupFiles: ['./vitest.setup.ts'],
    globalSetup: [
      path.resolve(__dirname, '../shared/src/__tests__/vitest.globalSetup.ts'),
    ],
    globalTeardown: [
      path.resolve(__dirname, '../shared/src/__tests__/vitest.globalTeardown.ts'),
    ],
  },
});
