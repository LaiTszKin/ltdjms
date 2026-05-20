/**
 * Loads environment variables from a .env file.
 * Supports:
 * - Lines starting with # as comments
 * - Quoted values (single or double quotes)
 * - Values containing = characters
 * - Whitespace around keys and values is trimmed
 */
export declare function loadDotEnv(filePath: string): Record<string, string>;
export declare function parseDotEnv(content: string): Record<string, string>;
