import { defineConfig } from 'vitest/config';
import path from 'path';

const sharedSrc = path.resolve(__dirname, '../shared/src');
const economySrc = path.resolve(__dirname, '../economy/src');
const gamesSrc = path.resolve(__dirname, '../games/src');
const shopSrc = path.resolve(__dirname, '../shop/src');
const userPanelSrc = path.resolve(__dirname, 'src');

export default defineConfig({
  resolve: {
    alias: [
      { find: /^@ltdjms\/shared\/(.+)$/, replacement: sharedSrc + '/$1' },
      { find: /^@ltdjms\/shared$/, replacement: sharedSrc },
      { find: /^@ltdjms\/economy$/, replacement: economySrc },
      { find: /^@ltdjms\/games$/, replacement: gamesSrc },
      { find: /^@ltdjms\/shop$/, replacement: shopSrc },
      { find: /^@ltdjms\/user-panel$/, replacement: userPanelSrc },
    ],
  },
  test: {
    include: ['src/**/*.test.ts'],
    testTimeout: 30000,
    fileParallelism: false,
    pool: 'forks',
    setupFiles: ['./vitest.setup.ts'],
    globalSetup: [path.resolve(__dirname, '../shared/src/__tests__/vitest.globalSetup.ts')],
    globalTeardown: [path.resolve(__dirname, '../shared/src/__tests__/vitest.globalTeardown.ts')],
  },
});
