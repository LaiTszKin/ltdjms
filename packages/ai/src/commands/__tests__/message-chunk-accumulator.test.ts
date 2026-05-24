import { describe, it, expect } from 'vitest';
import { MessageChunkAccumulator } from '../../services/message-chunk-accumulator.js';
import { MAX_MESSAGE_LENGTH } from '../../services/ai-chat-service.js';

/** UT-AIC-004 — MessageChunkAccumulatorTest.java parity */
describe('UT-AIC-004 message-chunk-accumulator parity', () => {
  it('testAccumulate_paragraphSplit_shouldReturnChunk', () => {
    const accumulator = new MessageChunkAccumulator();

    expect(accumulator.accumulate('第一段')).toEqual([]);
    const chunks = accumulator.accumulate('內容\n\n');
    expect(chunks).toHaveLength(1);
    expect(chunks[0]).toBe('第一段內容\n\n');
    expect(accumulator.accumulate('第二段內容')).toEqual([]);
  });

  it('testAccumulate_singleNewline_shouldNotSplit', () => {
    const accumulator = new MessageChunkAccumulator();
    expect(accumulator.accumulate('第一行\n')).toEqual([]);
    expect(accumulator.accumulate('第二行\n第三行')).toEqual([]);
    const chunks = accumulator.accumulate('\n\n');
    expect(chunks).toHaveLength(1);
    expect(chunks[0]).toBe('第一行\n第二行\n第三行\n\n');
  });

  it('testAccumulate_forceSplit_whenExceedsMaxLength', () => {
    const accumulator = new MessageChunkAccumulator();
    const longText = 'A'.repeat(2000);
    const chunks = accumulator.accumulate(longText);
    expect(chunks).toHaveLength(1);
    expect(chunks[0]).toHaveLength(MAX_MESSAGE_LENGTH);
    expect(accumulator.drain()).toHaveLength(20);
  });

  it('testDrain_shouldReturnRemainingContent', () => {
    const accumulator = new MessageChunkAccumulator();
    accumulator.accumulate('未完成的');
    accumulator.accumulate('內容');
    expect(accumulator.drain()).toBe('未完成的內容');
    expect(accumulator.drain()).toBe('');
  });

  it('testAccumulate_emptyDelta_shouldNotThrow', () => {
    const accumulator = new MessageChunkAccumulator();
    expect(accumulator.accumulate('')).toEqual([]);
    expect(accumulator.accumulate(null)).toEqual([]);
    accumulator.accumulate('內容');
    expect(accumulator.accumulate('')).toEqual([]);
  });

  it('testAccumulate_priority_paragraphOverForceSplit', () => {
    const accumulator = new MessageChunkAccumulator();
    expect(accumulator.accumulate('A'.repeat(1900))).toEqual([]);
    const chunks = accumulator.accumulate('\n\n');
    expect(chunks).toHaveLength(1);
    expect(chunks[0]).toHaveLength(1902);
    expect(accumulator.accumulate('更多內容')).toEqual([]);
    expect(accumulator.drain()).toBe('更多內容');
  });

  it('testDrain_trimsWhitespace', () => {
    const accumulator = new MessageChunkAccumulator();
    accumulator.accumulate('  內容  ');
    expect(accumulator.drain()).toBe('內容');
  });
});
