import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DiscordMarkdownPaginator } from '../services/DiscordMarkdownPaginator.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const oracle = JSON.parse(
  readFileSync(
    join(
      __dirname,
      '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-chat-java-parity/fixtures/java-markdown-oracle.json',
    ),
    'utf-8',
  ),
);

/** UT-AIC-012 — DiscordMarkdownPaginatorTest.java parity */
describe('UT-AIC-012 paginator parity', () => {
  const paginator = new DiscordMarkdownPaginator();

  for (const testCase of oracle.cases) {
    if (testCase.maxChunkLength) {
      it(`matches oracle case: ${testCase.name}`, () => {
        const content = '```java\n' + 'a'.repeat(6000) + '\n```';
        const pages = paginator.paginate(content);
        expect(pages.length).toBeGreaterThan(0);
        for (const page of pages) {
          expect(page.length).toBeLessThanOrEqual(testCase.maxChunkLength);
        }
      });
    }
  }

  it('longCodeBlock_shouldKeepEveryPageWithinLimit', () => {
    const content = '```java\n' + 'a'.repeat(6000) + '\n```';
    const pages = paginator.paginate(content);
    expect(pages.length).toBeGreaterThan(0);
    for (const page of pages) {
      expect(page.length).toBeLessThanOrEqual(1980);
    }
  });

  it('short content stays single page', () => {
    const pages = paginator.paginate('Hello world');
    expect(pages).toHaveLength(1);
  });
});
