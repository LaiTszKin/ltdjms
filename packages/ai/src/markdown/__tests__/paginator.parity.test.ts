import { describe, it, expect } from 'vitest';
import { DiscordMarkdownPaginator } from '../services/DiscordMarkdownPaginator.js';

/** UT-AIC-012 — DiscordMarkdownPaginatorTest.java parity */
describe('UT-AIC-012 paginator parity', () => {
  it('longCodeBlock_shouldKeepEveryPageWithinLimit', () => {
    const paginator = new DiscordMarkdownPaginator();
    const content = '```java\n' + 'a'.repeat(6000) + '\n```';
    const pages = paginator.paginate(content);
    expect(pages.length).toBeGreaterThan(0);
    for (const page of pages) {
      expect(page.length).toBeLessThanOrEqual(1980);
    }
  });

  it('short content stays single page', () => {
    const paginator = new DiscordMarkdownPaginator();
    const pages = paginator.paginate('Hello world');
    expect(pages).toHaveLength(1);
  });
});
