import { expect } from 'vitest';

type JsonRecord = Record<string, unknown>;

const EMBED_VOLATILE_KEYS = new Set(['timestamp', 'url', 'thumbnail', 'image', 'author', 'provider']);

/**
 * Normalizes a Discord embed (or embed-like object) for stable JSON snapshot comparison.
 * Strips volatile fields and sorts nested structures so Java oracle fixtures align with TS output.
 */
export function normalizeEmbedForSnapshot(embed: unknown): JsonRecord {
  if (embed == null || typeof embed !== 'object' || Array.isArray(embed)) {
    return {};
  }

  const source = embed as JsonRecord;
  const normalized: JsonRecord = {};

  for (const key of Object.keys(source).sort()) {
    if (EMBED_VOLATILE_KEYS.has(key)) {
      continue;
    }

    const value = source[key];
    if (value === undefined) {
      continue;
    }

    if (key === 'fields' && Array.isArray(value)) {
      normalized.fields = value.map((field) => normalizeValue(field));
      continue;
    }

    if (key === 'color' && typeof value === 'string') {
      normalized.color = Number.parseInt(value.replace('#', ''), 16);
      continue;
    }

    normalized[key] = normalizeValue(value);
  }

  return normalized;
}

/**
 * Recursively normalizes JSON-like values for parity comparison.
 */
export function normalizeValue(value: unknown): unknown {
  if (value == null) {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map((item) => normalizeValue(item));
  }

  if (typeof value !== 'object') {
    return value;
  }

  const record = value as JsonRecord;
  const normalized: JsonRecord = {};

  for (const key of Object.keys(record).sort()) {
    const nested = record[key];
    if (nested !== undefined) {
      normalized[key] = normalizeValue(nested);
    }
  }

  return normalized;
}

/**
 * Asserts structural parity between actual output and a Java-derived oracle fixture.
 * Uses Vitest equality with normalization applied to both sides.
 */
export function assertJsonParity(actual: unknown, oracle: unknown): void {
  expect(normalizeValue(actual)).toEqual(normalizeValue(oracle));
}

/**
 * Asserts embed parity using embed-specific normalization (volatile field stripping).
 */
export function assertEmbedParity(actual: unknown, oracle: unknown): void {
  expect(normalizeEmbedForSnapshot(actual)).toEqual(normalizeEmbedForSnapshot(oracle));
}
