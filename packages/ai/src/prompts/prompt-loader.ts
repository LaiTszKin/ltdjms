import { readdir, readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { DomainError, ok, err, type Result } from '@ltdjms/shared';

/**
 * Represents a single prompt section from a file.
 * Matches Java PromptSection record.
 */
export interface PromptSection {
  name: string;
  content: string;
}

/**
 * SystemPrompt value object — a collection of PromptSections.
 * Matches Java SystemPrompt class.
 */
export class SystemPrompt {
  readonly sections: PromptSection[];

  private constructor(sections: PromptSection[]) {
    this.sections = sections;
  }

  /** Creates an empty SystemPrompt. */
  static empty(): SystemPrompt {
    return new SystemPrompt([]);
  }

  /** Creates a SystemPrompt from sections. */
  static fromSections(sections: PromptSection[]): SystemPrompt {
    return new SystemPrompt(sections);
  }

  /** Combines all sections with double newline separators. */
  toCombinedString(): string {
    return this.sections
      .map((s) => s.content)
      .filter((c) => c.length > 0)
      .join('\n\n');
  }
}

/**
 * PromptLoader interface.
 *
 * IMPORTANT: Callers MUST check `isOk()` / `isErr()` before calling `getValue()`
 * or `getError()`. Calling `getValue()` on an Err result will throw.
 */
export interface PromptLoader {
  loadPrompts(agentEnabled: boolean): Promise<Result<SystemPrompt, DomainError>>;
}

/**
 * Default PromptLoader implementation.
 * Reads .md files from the filesystem, sorted alphabetically.
 * Caches loaded prompts in memory to avoid filesystem reads on every call.
 * Call invalidateCache() to force reload (e.g., after admin panel prompt update).
 * Matches Java DefaultPromptLoader.
 */
export class DefaultPromptLoader implements PromptLoader {
  private readonly promptsDirPath: string;
  private readonly maxFileSizeBytes: number;
  private cache: Map<string, { prompt: SystemPrompt; cachedAt: number }> = new Map();
  private static readonly CACHE_TTL_MS = 300_000; // 5 minutes

  constructor(promptsDirPath: string, maxFileSizeBytes: number = 1_048_576) {
    this.promptsDirPath = promptsDirPath;
    this.maxFileSizeBytes = maxFileSizeBytes;
  }

  invalidateCache(): void {
    this.cache.clear();
  }

  /**
   * Loads prompts from the filesystem.
   * - Reads all .md files from `promptsDirPath`
   * - If agentEnabled, additionally reads from `promptsDirPath/agent/`
   * - Files are sorted alphabetically within each group
   * - Directory not found: returns empty SystemPrompt with warning
   * - File too large or read failure: logs warning and skips the file
   */
  async loadPrompts(agentEnabled: boolean): Promise<Result<SystemPrompt, DomainError>> {
    const cacheKey = agentEnabled ? 'agent' : 'base';
    const cached = this.cache.get(cacheKey);
    if (cached && Date.now() - cached.cachedAt < DefaultPromptLoader.CACHE_TTL_MS) {
      return ok(cached.prompt);
    }

    const sections: PromptSection[] = [];

    // Load base prompts
    const baseSections = await this.loadDirectory(this.promptsDirPath);
    if (baseSections.isOk()) {
      sections.push(...baseSections.getValue());
    } else if (baseSections.getError().category !== 'PROMPT_DIR_NOT_FOUND') {
      return err(baseSections.getError()) as Result<SystemPrompt, DomainError>;
    }

    // Load agent prompts if enabled
    if (agentEnabled) {
      const agentDir = join(this.promptsDirPath, 'agent');
      const agentSections = await this.loadDirectory(agentDir);
      if (agentSections.isOk()) {
        sections.push(...agentSections.getValue());
      } else if (agentSections.getError().category !== 'PROMPT_DIR_NOT_FOUND') {
        return err(agentSections.getError()) as Result<SystemPrompt, DomainError>;
      }
    }

    const prompt = SystemPrompt.fromSections(sections);
    this.cache.set(cacheKey, { prompt, cachedAt: Date.now() });
    return ok(prompt);
  }

  private async loadDirectory(dirPath: string): Promise<Result<PromptSection[], DomainError>> {
    try {
      await stat(dirPath);
    } catch {
      return err(DomainError.promptDirNotFound(`Prompt directory not found: ${dirPath}`));
    }

    const entries = await readdir(dirPath, { withFileTypes: true });
    const mdFiles = entries
      .filter((e) => e.isFile() && e.name.endsWith('.md'))
      .map((e) => e.name)
      .sort();

    if (mdFiles.length === 0) {
      return ok([]);
    }

    const filePromises = mdFiles.map(async (fileName) => {
      const filePath = join(dirPath, fileName);

      try {
        const stats = await stat(filePath);
        if (stats.size > this.maxFileSizeBytes) {
          console.warn(
            `[prompt-loader] Prompt file too large: ${fileName} (${stats.size} bytes, max ${this.maxFileSizeBytes}), skipping`,
          );
          return null;
        }

        const content = await readFile(filePath, 'utf-8');
        const name = fileName.replace(/\.md$/, '');
        return { name, content } as PromptSection;
      } catch (cause) {
        console.warn(`[prompt-loader] Failed to read prompt file: ${fileName}, skipping`);
        return null;
      }
    });

    const results = await Promise.all(filePromises);
    const sections: PromptSection[] = results.filter((r): r is PromptSection => r !== null);

    return ok(sections);
  }
}
