import { describe, it, expect } from 'vitest';
import { MessageSplitter } from '../../services/MessageSplitter.js';

describe('MessageSplitter', () => {
  const splitter = new MessageSplitter(100); // Small max for testing

  it('should return empty array for empty content', () => {
    expect(splitter.split('')).toEqual([]);
  });

  it('should return single chunk for content within limit', () => {
    const content = 'Short message';
    const result = splitter.split(content);
    expect(result).toHaveLength(1);
    expect(result[0]).toBe(content);
  });

  it('should split at paragraph boundary', () => {
    const content = 'A'.repeat(60) + '\n\n' + 'B'.repeat(60);
    const result = splitter.split(content);
    expect(result.length).toBeGreaterThan(1);
    // First chunk ends before the paragraph break; second chunk starts with content after break
    expect(result[0].endsWith('A'.repeat(60))).toBe(true);
    expect(result[1]).toBe('B'.repeat(60));
  });

  it('should split at Chinese sentence boundary', () => {
    const content = 'A'.repeat(60) + '。' + 'B'.repeat(60);
    const result = splitter.split(content);
    expect(result.length).toBeGreaterThan(1);
    expect(result[0]).toContain('。');
  });

  it('should split at newline boundary', () => {
    const content = 'A'.repeat(60) + '\n' + 'B'.repeat(60);
    const result = splitter.split(content);
    expect(result.length).toBeGreaterThan(1);
  });

  it('should hard split at max length when no boundary found', () => {
    const content = 'A'.repeat(250); // No boundaries
    const result = splitter.split(content);
    expect(result.length).toBeGreaterThan(1);
    result.forEach((chunk) => {
      expect(chunk.length).toBeLessThanOrEqual(110); // Slightly over due to boundaries
    });
  });

  it('should trim leading whitespace from continuation chunks', () => {
    const content = '  A'.repeat(200); // Leading spaces
    const result = splitter.split(content);
    // Continuation chunks should not start with spaces
    for (let i = 1; i < result.length; i++) {
      expect(result[i].startsWith(' ')).toBe(false);
    }
  });
});

describe('MessageSplitter with default max length', () => {
  const splitter = new MessageSplitter();

  it('should not split content under 1980 chars', () => {
    const content = 'A'.repeat(1500);
    const result = splitter.split(content);
    expect(result).toHaveLength(1);
  });

  it('should split content over 1980 chars', () => {
    const content = 'A'.repeat(2500);
    const result = splitter.split(content);
    expect(result.length).toBeGreaterThan(1);
  });
});
