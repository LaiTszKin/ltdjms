import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs';
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
 */
export interface PromptLoader {
  loadPrompts(agentEnabled: boolean): Result<SystemPrompt, DomainError>;
}

/**
 * Default PromptLoader implementation.
 * Reads .md files from the filesystem, sorted alphabetically.
 * Matches Java DefaultPromptLoader.
 */
export class DefaultPromptLoader implements PromptLoader {
  private readonly promptsDirPath: string;
  private readonly maxFileSizeBytes: number;

  constructor(promptsDirPath: string, maxFileSizeBytes: number = 1_048_576) {
    this.promptsDirPath = promptsDirPath;
    this.maxFileSizeBytes = maxFileSizeBytes;
  }

  /**
   * Loads prompts from the filesystem.
   * - Reads all .md files from `promptsDirPath`
   * - If agentEnabled, additionally reads from `promptsDirPath/agent/`
   * - Files are sorted alphabetically within each group
   * - Directory not found: returns empty SystemPrompt with warning
   * - File too large: returns DomainError PROMPT_FILE_TOO_LARGE
   */
  loadPrompts(agentEnabled: boolean): Result<SystemPrompt, DomainError> {
    const sections: PromptSection[] = [];

    // Load base prompts
    const baseSections = this.loadDirectory(this.promptsDirPath);
    if (baseSections.isOk()) {
      sections.push(...baseSections.getValue());
    } else if (baseSections.getError().category !== 'PROMPT_DIR_NOT_FOUND') {
      return err(baseSections.getError()) as Result<SystemPrompt, DomainError>;
    }

    // Load agent prompts if enabled
    if (agentEnabled) {
      const agentDir = join(this.promptsDirPath, 'agent');
      const agentSections = this.loadDirectory(agentDir);
      if (agentSections.isOk()) {
        sections.push(...agentSections.getValue());
      } else if (agentSections.getError().category !== 'PROMPT_DIR_NOT_FOUND') {
        return err(agentSections.getError()) as Result<SystemPrompt, DomainError>;
      }
    }

    return ok(SystemPrompt.fromSections(sections));
  }

  private loadDirectory(dirPath: string): Result<PromptSection[], DomainError> {
    if (!existsSync(dirPath)) {
      return err(DomainError.promptDirNotFound(
        `Prompt directory not found: ${dirPath}`,
      ));
    }

    const entries = readdirSync(dirPath, { withFileTypes: true });
    const mdFiles = entries
      .filter((e) => e.isFile() && e.name.endsWith('.md'))
      .map((e) => e.name)
      .sort();

    if (mdFiles.length === 0) {
      return ok([]);
    }

    const sections: PromptSection[] = [];
    for (const fileName of mdFiles) {
      const filePath = join(dirPath, fileName);

      try {
        const stats = statSync(filePath);
        if (stats.size > this.maxFileSizeBytes) {
          return err(DomainError.promptFileTooLarge(
            `Prompt file too large: ${fileName} (${stats.size} bytes, max ${this.maxFileSizeBytes})`,
          ));
        }

        const content = readFileSync(filePath, 'utf-8');
        const name = fileName.replace(/\.md$/, '');
        sections.push({ name, content });
      } catch (cause) {
        return err(DomainError.promptReadFailed(
          `Failed to read prompt file: ${fileName}`,
          cause instanceof Error ? cause : undefined,
        ));
      }
    }

    return ok(sections);
  }
}
