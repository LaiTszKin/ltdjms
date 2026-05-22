import { describe, it, expect } from 'vitest';
import { ok, err, okVoid, isOk, isErr, Ok, Err, Unit } from '../result.js';

describe('Result<T, E>', () => {
  describe('ok()', () => {
    it('creates a success result', () => {
      const result = ok<number, string>(42);
      expect(result.isOk()).toBe(true);
      expect(result.isErr()).toBe(false);
      expect(result.getValue()).toBe(42);
    });

    it('supports type guards (isOk)', () => {
      const result = ok<number, string>(42);
      expect(isOk(result)).toBe(true);
      expect(isErr(result)).toBe(false);
    });

    it('getValue returns the value', () => {
      const result = ok<number, string>(42);
      expect(result.getValue()).toBe(42);
    });

    it('getError throws on Ok', () => {
      const result = ok<number, string>(42);
      expect(() => result.getError()).toThrow('Cannot get error from Ok');
    });

    it('getOrElse returns the value', () => {
      const result = ok<number, string>(42);
      expect(result.getOrElse(0)).toBe(42);
    });

    it('rejects null value', () => {
      expect(() => ok<null, string>(null)).toThrow('value must not be null or undefined');
    });

    it('rejects undefined value', () => {
      expect(() => ok<undefined, string>(undefined as any)).toThrow(
        'value must not be null or undefined',
      );
    });
  });

  describe('err()', () => {
    it('creates an error result', () => {
      const result = err<number, string>('something went wrong');
      expect(result.isOk()).toBe(false);
      expect(result.isErr()).toBe(true);
      expect(result.getError()).toBe('something went wrong');
    });

    it('supports type guards (isErr)', () => {
      const result = err<number, string>('error');
      expect(isErr(result)).toBe(true);
      expect(isOk(result)).toBe(false);
    });

    it('getValue throws on Err', () => {
      const result = err<number, string>('error');
      expect(() => result.getValue()).toThrow('Cannot get value from Err');
    });

    it('getError returns the error', () => {
      const result = err<number, string>('error');
      expect(result.getError()).toBe('error');
    });

    it('getOrElse returns default value', () => {
      const result = err<number, string>('error');
      expect(result.getOrElse(99)).toBe(99);
    });

    it('rejects null error', () => {
      expect(() => err<number, null>(null)).toThrow('error must not be null or undefined');
    });

    it('rejects undefined error', () => {
      expect(() => err<number, undefined>(undefined as any)).toThrow(
        'error must not be null or undefined',
      );
    });
  });

  describe('okVoid()', () => {
    it('creates a success result with Unit value', () => {
      const result = okVoid<string>();
      expect(result.isOk()).toBe(true);
      expect(result.getValue()).toBe(Unit.INSTANCE);
    });
  });

  describe('map()', () => {
    it('maps over Ok value', () => {
      const result = ok<number, string>(10);
      const mapped = result.map((x) => x * 2);
      expect(mapped.getValue()).toBe(20);
    });

    it('skips map on Err', () => {
      const result = err<number, string>('fail');
      const mapped = result.map((x) => x * 2);
      expect(mapped.isErr()).toBe(true);
      expect(mapped.getError()).toBe('fail');
    });
  });

  describe('flatMap()', () => {
    it('flatMaps over Ok value', () => {
      const result = ok<number, string>(10);
      const chained = result.flatMap((x) => ok<number, string>(x * 3));
      expect(chained.getValue()).toBe(30);
    });

    it('short-circuits flatMap on Err', () => {
      const result = err<number, string>('fail');
      const chained = result.flatMap((x) => ok<number, string>(x * 3));
      expect(chained.isErr()).toBe(true);
      expect(chained.getError()).toBe('fail');
    });

    it('chains multiple flatMaps', () => {
      const validate = (n: number) =>
        n > 0 ? ok<number, string>(n) : err<number, string>('negative');
      const double = (n: number) => ok<number, string>(n * 2);

      const result = ok<number, string>(5).flatMap(validate).flatMap(double);
      expect(result.getValue()).toBe(10);
    });

    it('stops on first error in chain', () => {
      const validate = (n: number) =>
        n > 0 ? ok<number, string>(n) : err<number, string>('negative');
      const result = err<number, string>('initial error')
        .flatMap(validate)
        .flatMap((n) => ok(n * 2));
      expect(result.getError()).toBe('initial error');
    });
  });

  describe('mapError()', () => {
    it('maps over Err error', () => {
      const result = err<number, string>('fail');
      const mapped = result.mapError((e) => `ERR: ${e}`);
      expect(mapped.getError()).toBe('ERR: fail');
    });

    it('skips mapError on Ok', () => {
      const result = ok<number, string>(42);
      const mapped = result.mapError((e) => `ERR: ${e}`);
      expect(mapped.getValue()).toBe(42);
    });
  });

  describe('toString / instance checks', () => {
    it('Ok has _tag ok', () => {
      const result = ok<number, string>(1);
      expect(result._tag).toBe('ok');
    });

    it('Err has _tag err', () => {
      const result = err<number, string>('error');
      expect(result._tag).toBe('err');
    });
  });
});
