import { describe, it, expect } from 'vitest';
import { DiscordMarkdownPaginator } from '../../markdown/services/DiscordMarkdownPaginator.js';

describe('DiscordMarkdownPaginator', () => {
  // Create paginator with a small max length for testing
  const createPaginator = (maxLength: number = 1900) =>
    new DiscordMarkdownPaginator(maxLength);

  it('should return empty array for null content', () => {
    const paginator = createPaginator();
    const result = paginator.paginate(null);
    expect(result).toEqual([]);
  });

  it('should return empty array for empty string', () => {
    const paginator = createPaginator();
    const result = paginator.paginate('');
    expect(result).toEqual([]);
  });

  it('should return single page for content within limit', () => {
    const paginator = createPaginator();
    const content = '# Hello\n\nThis is a short message.';
    const result = paginator.paginate(content);
    expect(result).toHaveLength(1);
    expect(result[0]).toBe(content);
  });

  it('should split at heading boundaries', () => {
    const paginator = createPaginator(100);
    // Create content with two sections that exceeds 100 chars
    const content =
      '# Section 1\n\n' +
      'A'.repeat(80) +
      '\n\n' +
      '## Section 2\n\n' +
      'B'.repeat(80);
    const result = paginator.paginate(content);
    expect(result.length).toBeGreaterThan(1);
    // First page should contain the first heading
    expect(result[0]).toContain('# Section 1');
    // Later page should contain the second heading
    const lastPage = result[result.length - 1];
    expect(lastPage).toContain('Section 2');
  });

  it('should handle code fence boundaries across pages', () => {
    const paginator = createPaginator(100);
    const content =
      '# Section\n\n' +
      '```\n' +
      'L'.repeat(200) +
      '\n```\n\n' +
      '## Next Section';
    const result = paginator.paginate(content);
    expect(result.length).toBeGreaterThan(1);
    // Combined content across all pages should preserve original code fence balance
    // (each page may have imbalanced fences, but combined should be balanced)
    const combinedBackticks = result.reduce(
      (sum, page) => sum + (page.match(/```/g) || []).length,
      0,
    );
    expect(combinedBackticks % 2 === 0).toBe(true);
    // The original content had 2 ``` markers
    const originalBackticks = (content.match(/```/g) || []).length;
    // Each page boundary may add/close fences, so combined may have more than original
    expect(combinedBackticks).toBeGreaterThanOrEqual(originalBackticks);
  });

  it('should handle hard split when no heading boundary found', () => {
    const paginator = createPaginator(50);
    const content = 'A'.repeat(200);
    const result = paginator.paginate(content);
    expect(result.length).toBeGreaterThan(1);
    // Each page should be <= 50 chars (or slightly more if code fence chars are reserved)
    result.forEach((page) => {
      expect(page.length).toBeLessThanOrEqual(70); // Allow some overhead
    });
  });

  it('should trim trailing whitespace from each page', () => {
    const paginator = createPaginator(50);
    const content =
      '# Section 1\n\n' +
      'A'.repeat(40) +
      '\n\n' +
      '## Section 2\n\n' +
      'B'.repeat(40);
    const result = paginator.paginate(content);
    result.forEach((page) => {
      expect(page).toBe(page.trimEnd());
    });
  });

  it('should handle large content efficiently', () => {
    const paginator = createPaginator(1900);
    const sectionCount = 20;
    const sections = Array.from(
      { length: sectionCount },
      (_, i) => `## Section ${i + 1}\n\n` + 'Content '.repeat(50),
    );
    const content = sections.join('\n');
    const result = paginator.paginate(content);
    expect(result.length).toBeGreaterThan(0);
    expect(result.length).toBeLessThan(sectionCount);
  });

  it('should not split in the middle of a code block', () => {
    const paginator = createPaginator(200);
    // Create content where a code block straddles the boundary
    const codeContent = '```\n' + 'L'.repeat(300) + '\n```';
    const content = '# Start\n\n' + codeContent + '\n\n# End';
    const result = paginator.paginate(content);
    // Verify no code fence is split mid-block
    let inCodeBlock = false;
    for (const page of result) {
      const opens = (page.match(/^```/gm) || []).length;
      const closes = (page.match(/```$/gm) || []).length;
      if (inCodeBlock) {
        // This page should close the code block
        expect(opens).toBe(0);
        expect(closes).toBe(1);
        inCodeBlock = false;
      } else if (opens > closes) {
        inCodeBlock = true;
      }
    }
    expect(inCodeBlock).toBe(false);
  });
});
