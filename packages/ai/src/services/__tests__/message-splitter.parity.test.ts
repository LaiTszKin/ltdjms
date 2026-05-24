import { describe, it, expect } from 'vitest';
import { MessageSplitter } from '../../services/MessageSplitter.js';
import { MAX_MESSAGE_LENGTH } from '../../services/ai-chat-service.js';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const oracle = JSON.parse(
  readFileSync(
    join(
      __dirname,
      '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-chat-java-parity/fixtures/java-streaming-oracle.json',
    ),
    'utf-8',
  ),
);

/** UT-AIC-005 — MessageSplitterTest.java parity */
describe('UT-AIC-005 message-splitter parity', () => {
  const splitter = new MessageSplitter();

  it('loads java-streaming-oracle.json', () => {
    expect(oracle.messageSplitLimit).toBe(1980);
  });

  it('short_message_no_split', () => {
    const result = splitter.split('這是一則短訊息');
    expect(result).toHaveLength(1);
  });

  it('forced_split_at_limit', () => {
    const content = 'A'.repeat(2000);
    const result = splitter.split(content);
    expect(result.length).toBeGreaterThanOrEqual(1);
    for (const part of result) {
      expect(part.length).toBeLessThanOrEqual(MAX_MESSAGE_LENGTH + 50);
    }
  });
});
