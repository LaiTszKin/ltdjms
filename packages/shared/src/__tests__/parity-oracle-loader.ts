import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Loads a JSON parity oracle fixture relative to the calling test module.
 */
export function loadParityOracle<T>(importMetaUrl: string, relativeFixturePath: string): T {
  const baseDir = dirname(fileURLToPath(importMetaUrl));
  const fixturePath = join(baseDir, relativeFixturePath);
  return JSON.parse(readFileSync(fixturePath, 'utf-8')) as T;
}
