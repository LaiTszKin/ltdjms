import { readdir, readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { DomainError, ok, err, type Result } from '@ltdjms/shared';

/**
 * Represents a single prompt section from a file.
 * Matches Java PromptSection record.
 */
export interface PromptSection {
  title: string;
  content: string;
}

/** Formats a section as === TITLE === + content. Matches Java PromptSection.toFormattedString(). */
export function formatPromptSection(section: PromptSection): string {
  if (!section.title && !section.content) {
    return '';
  }
  if (!section.content.trim()) {
    return `=== ${section.title} ===`;
  }
  return `=== ${section.title} ===\n${section.content}`;
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

  static empty(): SystemPrompt {
    return new SystemPrompt([]);
  }

  static fromSections(sections: PromptSection[]): SystemPrompt {
    return new SystemPrompt(sections);
  }

  isEmpty(): boolean {
    return this.sections.length === 0;
  }

  sectionCount(): number {
    return this.sections.length;
  }

  /** Combines sections with === TITLE === separators. Matches Java toCombinedString(). */
  toCombinedString(): string {
    if (this.isEmpty()) {
      return '';
    }

    const parts: string[] = [];
    for (const section of this.sections) {
      const formatted = formatPromptSection(section);
      if (formatted) {
        parts.push(formatted);
      }
    }
    return parts.join('\n\n');
  }
}

export interface PromptLoader {
  loadPrompts(agentEnabled: boolean): Promise<Result<SystemPrompt, DomainError>>;
}

/**
 * Default PromptLoader — loads system/ (required) and agent/ (optional) subdirectories.
 * Matches Java DefaultPromptLoader.
 */
export class DefaultPromptLoader implements PromptLoader {
  private cache: Map<string, { prompt: SystemPrompt; cachedAt: number }> = new Map();
  private static readonly CACHE_TTL_MS = 300_000;

  constructor(
    private readonly promptsDirPath: string,
    private readonly maxFileSizeBytes: number = 1_048_576,
  ) {}

  invalidateCache(): void {
    this.cache.clear();
  }

  async loadPrompts(agentEnabled: boolean): Promise<Result<SystemPrompt, DomainError>> {
    const cacheKey = agentEnabled ? 'agent' : 'base';
    const cached = this.cache.get(cacheKey);
    if (cached && Date.now() - cached.cachedAt < DefaultPromptLoader.CACHE_TTL_MS) {
      return ok(cached.prompt);
    }

    const systemResult = await this.loadFromDirectory('system');
    if (systemResult.isErr()) {
      return systemResult;
    }

    let finalPrompt = systemResult.getValue();
    if (agentEnabled) {
      const agentResult = await this.loadFromDirectory('agent');
      if (agentResult.isOk()) {
        finalPrompt = SystemPrompt.fromSections([
          ...finalPrompt.sections,
          ...agentResult.getValue().sections,
        ]);
      }
    }

    this.cache.set(cacheKey, { prompt: finalPrompt, cachedAt: Date.now() });
    return ok(finalPrompt);
  }

  private async loadFromDirectory(subDir: string): Promise<Result<SystemPrompt, DomainError>> {
    const targetDir = join(this.promptsDirPath, subDir);

    try {
      const dirStat = await stat(targetDir);
      if (!dirStat.isDirectory()) {
        throw new Error('not a directory');
      }
    } catch {
      if (subDir === 'system') {
        return err(
          DomainError.unexpectedFailure(`Required prompts directory not found: ${subDir}`),
        );
      }
      return err(DomainError.unexpectedFailure(`Optional prompts directory not found: ${subDir}`));
    }

    const entries = await readdir(targetDir, { withFileTypes: true });
    const mdFiles = entries
      .filter((e) => e.isFile() && e.name.endsWith('.md') && !e.name.startsWith('.'))
      .map((e) => e.name)
      .sort();

    const sections: PromptSection[] = [];
    for (const fileName of mdFiles) {
      const filePath = join(targetDir, fileName);
      try {
        const fileStat = await stat(filePath);
        if (fileStat.size > this.maxFileSizeBytes) {
          console.warn(
            `[prompt-loader] Prompt file too large: ${fileName} (${fileStat.size} bytes), skipping`,
          );
          continue;
        }
        const content = await readFile(filePath, 'utf-8');
        sections.push({
          title: normalizeTitle(fileName),
          content,
        });
      } catch {
        console.warn(`[prompt-loader] Failed to read prompt file: ${fileName}, skipping`);
      }
    }

    sections.sort((a, b) => a.title.localeCompare(b.title));
    return ok(SystemPrompt.fromSections(sections));
  }
}

/** Normalizes filename to section title. Matches Java normalizeTitle(). */
export function normalizeTitle(fileName: string): string {
  let title = fileName.endsWith('.md') ? fileName.slice(0, -3) : fileName;
  if (!title.trim()) {
    return 'UNTITLED';
  }
  title = title.replace(/-/g, ' ').replace(/_/g, ' ');
  return title
    .split('')
    .map((c) => (c >= 'a' && c <= 'z' ? c.toUpperCase() : c))
    .join('');
}
