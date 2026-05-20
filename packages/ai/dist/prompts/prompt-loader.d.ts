import { DomainError, type Result } from '@ltdjms/shared';
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
export declare class SystemPrompt {
    readonly sections: PromptSection[];
    private constructor();
    /** Creates an empty SystemPrompt. */
    static empty(): SystemPrompt;
    /** Creates a SystemPrompt from sections. */
    static fromSections(sections: PromptSection[]): SystemPrompt;
    /** Combines all sections with double newline separators. */
    toCombinedString(): string;
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
export declare class DefaultPromptLoader implements PromptLoader {
    private readonly promptsDirPath;
    private readonly maxFileSizeBytes;
    constructor(promptsDirPath: string, maxFileSizeBytes?: number);
    /**
     * Loads prompts from the filesystem.
     * - Reads all .md files from `promptsDirPath`
     * - If agentEnabled, additionally reads from `promptsDirPath/agent/`
     * - Files are sorted alphabetically within each group
     * - Directory not found: returns empty SystemPrompt with warning
     * - File too large: returns DomainError PROMPT_FILE_TOO_LARGE
     */
    loadPrompts(agentEnabled: boolean): Result<SystemPrompt, DomainError>;
    private loadDirectory;
}
