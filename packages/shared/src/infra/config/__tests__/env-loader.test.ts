import { describe, it, expect } from 'vitest';
import { parseDotEnv } from '../env-loader.js';

describe('parseDotEnv', () => {
  it('parses simple key=value pairs', () => {
    const result = parseDotEnv('KEY=value\nFOO=bar');
    expect(result).toEqual({ KEY: 'value', FOO: 'bar' });
  });

  it('skips empty lines and comments', () => {
    const result = parseDotEnv('# comment\n\nKEY=value\n# another comment\nFOO=bar');
    expect(result).toEqual({ KEY: 'value', FOO: 'bar' });
  });

  it('strips surrounding quotes from values', () => {
    const result = parseDotEnv('KEY="quoted value"\nFOO=\'single quotes\'');
    expect(result).toEqual({ KEY: 'quoted value', FOO: 'single quotes' });
  });

  it('preserves = characters in values', () => {
    const result = parseDotEnv('KEY=value=with=equals');
    expect(result).toEqual({ KEY: 'value=with=equals' });
  });

  it('trims whitespace around keys and values', () => {
    const result = parseDotEnv('  KEY  =  value  ');
    expect(result).toEqual({ KEY: 'value' });
  });

  it('ignores lines without equals sign', () => {
    const result = parseDotEnv('KEY=value\nINVALID_LINE');
    expect(result).toEqual({ KEY: 'value' });
  });

  it('handles empty content', () => {
    const result = parseDotEnv('');
    expect(result).toEqual({});
  });

  it('handles values with inline comments as part of value', () => {
    const result = parseDotEnv('DB_URL=postgres://localhost:5432/db');
    expect(result).toEqual({ DB_URL: 'postgres://localhost:5432/db' });
  });

  it('handles single-letter keys and values', () => {
    const result = parseDotEnv('A=b\nC=d');
    expect(result).toEqual({ A: 'b', C: 'd' });
  });
});
