import 'reflect-metadata';
import { describe, it, expect, vi } from 'vitest';
import { MarkdownValidatingAIChatService } from '../services/MarkdownValidatingAIChatService.js';
import { DiscordMarkdownSanitizer } from '../services/DiscordMarkdownSanitizer.js';
import { RegexBasedAutoFixer } from '../autofix/RegexBasedAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { DiscordMarkdownPaginator } from '../services/DiscordMarkdownPaginator.js';
import { DiscordMarkdownStreamProcessor } from '../services/DiscordMarkdownStreamProcessor.js';
import { MarkdownHeadingSegmenter } from '../services/MarkdownHeadingSegmenter.js';
import { AIServiceConfig } from '../../config/ai-service-config.js';
import { StreamChunkType } from '../../services/ai-chat-service.js';
import type { AIChatService, StreamingResponseHandler } from '../../services/ai-chat-service.js';
import { ok } from '@ltdjms/shared';

/** UT-AIC-013 — MarkdownValidatingAIChatServiceTest_* parity */
describe('UT-AIC-013 validating-chat-service parity', () => {
  const config = AIServiceConfig.fromValues({
    baseUrl: 'https://api.test.com/v1',
    apiKey: 'key',
    model: 'gpt-4o-mini',
    enableMarkdownValidation: true,
    streamingBypassValidation: false,
  });

  it('validResponseOnFirstTry_shouldReturnDirectly', async () => {
    const delegate = {
      config,
      generateResponse: vi.fn().mockResolvedValue(ok(['# Hello\n\nWorld'])),
    } as unknown as AIChatService;

    const service = new MarkdownValidatingAIChatService(
      delegate,
      new DiscordMarkdownSanitizer(),
      new RegexBasedAutoFixer(),
      new CommonMarkValidator(),
      new DiscordMarkdownPaginator(),
    );

    const result = await service.generateResponse('g', 'c', 'u', 'msg');
    expect(result.isOk()).toBe(true);
    expect(result.getValue().length).toBeGreaterThan(0);
  });

  it('stream processor emits pages incrementally', () => {
    const processor = new DiscordMarkdownStreamProcessor(
      new MarkdownHeadingSegmenter(),
      new CommonMarkValidator(),
      new RegexBasedAutoFixer(),
      new DiscordMarkdownSanitizer(),
      new DiscordMarkdownPaginator(),
    );
    const pages = processor.onChunk('# Title\n\nBody paragraph.');
    expect(Array.isArray(pages)).toBe(true);
    const remaining = processor.flush();
    expect(Array.isArray(remaining)).toBe(true);
  });

  it('stream processor warns when markdown remains invalid after autofix', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const invalidResult = {
      _tag: 'invalid' as const,
      errors: [
        {
          errorType: 'HEADING_FORMAT' as const,
          line: 1,
          column: 1,
          context: '#Bad',
          suggestion: 'Add space after #',
        },
      ],
    };
    const validator = {
      validate: vi.fn().mockReturnValue(invalidResult),
    };
    const autoFixer = {
      autoFix: vi.fn((input: string) => input),
    };

    const processor = new DiscordMarkdownStreamProcessor(
      new MarkdownHeadingSegmenter(),
      validator,
      autoFixer,
      new DiscordMarkdownSanitizer(),
      new DiscordMarkdownPaginator(),
    );

    processor.onChunk('#Bad heading\n\n');
    const pages = processor.flush();
    expect(Array.isArray(pages)).toBe(true);
    expect(warnSpy).toHaveBeenCalledWith(
      '[DiscordMarkdownStreamProcessor] Markdown still invalid after autofix:',
      invalidResult.errors,
    );
    warnSpy.mockRestore();
  });

  it('streaming passes REASONING through unchanged', async () => {
    const chunks: Array<{ chunk: string; type?: StreamChunkType }> = [];
    const delegate = {
      config,
      generateStreamingResponse: vi.fn(
        async (
          _g: string,
          _c: string,
          _u: string,
          _m: string,
          handler: StreamingResponseHandler,
        ) => {
          await handler.onChunk('thinking', false, null, StreamChunkType.REASONING);
          await handler.onChunk('', true, null, StreamChunkType.REASONING);
        },
      ),
    } as unknown as AIChatService;

    const service = new MarkdownValidatingAIChatService(
      delegate,
      new DiscordMarkdownSanitizer(),
      new RegexBasedAutoFixer(),
      new CommonMarkValidator(),
      new DiscordMarkdownPaginator(),
    );

    await service.generateStreamingResponse('g', 'c', 'u', 'msg', {
      onChunk: async (chunk, _complete, _err, type) => {
        chunks.push({ chunk, type });
      },
    });

    expect(chunks.some((c) => c.type === StreamChunkType.REASONING)).toBe(true);
  });
});
