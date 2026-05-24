import 'reflect-metadata';
import { describe, it, expect, beforeEach } from 'vitest';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { DefaultPromptLoader, SystemPrompt } from '../../prompts/prompt-loader.js';
import { DomainErrorCategory } from '@ltdjms/shared';

/** UT-AIC-008 — PromptLoaderTest.java parity */
describe('UT-AIC-008 prompt-loader parity', () => {
  let tempDir: string;

  beforeEach(async () => {
    tempDir = await mkdtemp(join(tmpdir(), 'prompts-'));
  });

  it('loads single system file', async () => {
    const systemDir = join(tempDir, 'system');
    await mkdir(systemDir, { recursive: true });
    await writeFile(join(systemDir, 'personality.md'), 'You are a helpful bot.');

    const loader = new DefaultPromptLoader(tempDir);
    const result = await loader.loadPrompts(false);

    expect(result.isOk()).toBe(true);
    const prompt = result.getValue();
    expect(prompt.isEmpty()).toBe(false);
    expect(prompt.sectionCount()).toBe(1);
    expect(prompt.sections[0].title).toBe('PERSONALITY');
    expect(prompt.sections[0].content).toBe('You are a helpful bot.');
  });

  it('returns error when system directory missing', async () => {
    const loader = new DefaultPromptLoader(tempDir);
    const result = await loader.loadPrompts(false);
    expect(result.isErr()).toBe(true);
    if (result.isErr()) {
      expect(result.getError().category).toBe(DomainErrorCategory.UNEXPECTED_FAILURE);
    }
  });

  it('agentEnabled loads system + agent', async () => {
    await mkdir(join(tempDir, 'system'), { recursive: true });
    await mkdir(join(tempDir, 'agent'), { recursive: true });
    await writeFile(join(tempDir, 'system', 'rules.md'), 'Rule 1');
    await writeFile(join(tempDir, 'agent', 'Agent-說明.md'), '# Agent 說明');

    const loader = new DefaultPromptLoader(tempDir);
    const result = await loader.loadPrompts(true);

    expect(result.isOk()).toBe(true);
    expect(result.getValue().sectionCount()).toBe(2);
  });

  it('toCombinedString uses === TITLE === format', () => {
    const prompt = SystemPrompt.fromSections([
      { title: 'RULES', content: 'Be helpful' },
      { title: 'FORMAT', content: 'Use markdown' },
    ]);
    const combined = prompt.toCombinedString();
    expect(combined).toContain('=== FORMAT ===');
    expect(combined).toContain('=== RULES ===');
  });
});
