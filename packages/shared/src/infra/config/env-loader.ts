import { readFileSync } from 'node:fs';

/**
 * Loads environment variables from a .env file.
 * Uses synchronous readFileSync because this is called once at startup
 * during configuration initialization, before any async operations begin.
 * The blocking nature has no measurable performance impact in this context.
 *
 * Supports:
 * - Lines starting with # as comments
 * - Quoted values (single or double quotes)
 * - Values containing = characters
 * - Whitespace around keys and values is trimmed
 */
export function loadDotEnv(filePath: string): Record<string, string> {
  try {
    const content = readFileSync(filePath, 'utf-8');
    return parseDotEnv(content);
  } catch {
    return {};
  }
}

export function parseDotEnv(content: string): Record<string, string> {
  const result: Record<string, string> = {};

  for (const rawLine of content.split('\n')) {
    const line = rawLine.trim();

    // Skip empty lines and comments
    if (!line || line.startsWith('#')) {
      continue;
    }

    // Find the first equals sign
    const eqIndex = line.indexOf('=');
    if (eqIndex <= 0) {
      continue;
    }

    const key = line.slice(0, eqIndex).trim();
    let value = line.slice(eqIndex + 1).trim();

    // Strip surrounding quotes
    value = stripQuotes(value);

    if (key) {
      result[key] = value;
    }
  }

  return result;
}

function stripQuotes(value: string): string {
  if (value.length >= 2) {
    const first = value[0];
    const last = value[value.length - 1];

    if (first === '"' && last === '"') {
      return value.slice(1, -1);
    }
    if (first === "'" && last === "'") {
      return value.slice(1, -1);
    }
  }
  return value;
}
