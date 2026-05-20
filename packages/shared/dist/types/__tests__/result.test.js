import { describe, it, expect } from 'vitest';
import { ok, err, okVoid, isOk, isErr, Unit } from '../result.js';
describe('Result<T, E>', () => {
    describe('ok()', () => {
        it('creates a success result', () => {
            const result = ok(42);
            expect(result.isOk()).toBe(true);
            expect(result.isErr()).toBe(false);
            expect(result.getValue()).toBe(42);
        });
        it('supports type guards (isOk)', () => {
            const result = ok(42);
            expect(isOk(result)).toBe(true);
            expect(isErr(result)).toBe(false);
        });
        it('getValue returns the value', () => {
            const result = ok(42);
            expect(result.getValue()).toBe(42);
        });
        it('getError throws on Ok', () => {
            const result = ok(42);
            expect(() => result.getError()).toThrow('Cannot get error from Ok');
        });
        it('getOrElse returns the value', () => {
            const result = ok(42);
            expect(result.getOrElse(0)).toBe(42);
        });
        it('rejects null value', () => {
            expect(() => ok(null)).toThrow('value must not be null or undefined');
        });
        it('rejects undefined value', () => {
            expect(() => ok(undefined)).toThrow('value must not be null or undefined');
        });
    });
    describe('err()', () => {
        it('creates an error result', () => {
            const result = err('something went wrong');
            expect(result.isOk()).toBe(false);
            expect(result.isErr()).toBe(true);
            expect(result.getError()).toBe('something went wrong');
        });
        it('supports type guards (isErr)', () => {
            const result = err('error');
            expect(isErr(result)).toBe(true);
            expect(isOk(result)).toBe(false);
        });
        it('getValue throws on Err', () => {
            const result = err('error');
            expect(() => result.getValue()).toThrow('Cannot get value from Err');
        });
        it('getError returns the error', () => {
            const result = err('error');
            expect(result.getError()).toBe('error');
        });
        it('getOrElse returns default value', () => {
            const result = err('error');
            expect(result.getOrElse(99)).toBe(99);
        });
        it('rejects null error', () => {
            expect(() => err(null)).toThrow('error must not be null or undefined');
        });
        it('rejects undefined error', () => {
            expect(() => err(undefined)).toThrow('error must not be null or undefined');
        });
    });
    describe('okVoid()', () => {
        it('creates a success result with Unit value', () => {
            const result = okVoid();
            expect(result.isOk()).toBe(true);
            expect(result.getValue()).toBe(Unit.INSTANCE);
        });
    });
    describe('map()', () => {
        it('maps over Ok value', () => {
            const result = ok(10);
            const mapped = result.map((x) => x * 2);
            expect(mapped.getValue()).toBe(20);
        });
        it('skips map on Err', () => {
            const result = err('fail');
            const mapped = result.map((x) => x * 2);
            expect(mapped.isErr()).toBe(true);
            expect(mapped.getError()).toBe('fail');
        });
    });
    describe('flatMap()', () => {
        it('flatMaps over Ok value', () => {
            const result = ok(10);
            const chained = result.flatMap((x) => ok(x * 3));
            expect(chained.getValue()).toBe(30);
        });
        it('short-circuits flatMap on Err', () => {
            const result = err('fail');
            const chained = result.flatMap((x) => ok(x * 3));
            expect(chained.isErr()).toBe(true);
            expect(chained.getError()).toBe('fail');
        });
        it('chains multiple flatMaps', () => {
            const validate = (n) => n > 0 ? ok(n) : err('negative');
            const double = (n) => ok(n * 2);
            const result = ok(5)
                .flatMap(validate)
                .flatMap(double);
            expect(result.getValue()).toBe(10);
        });
        it('stops on first error in chain', () => {
            const validate = (n) => n > 0 ? ok(n) : err('negative');
            const result = err('initial error')
                .flatMap(validate)
                .flatMap((n) => ok(n * 2));
            expect(result.getError()).toBe('initial error');
        });
    });
    describe('mapError()', () => {
        it('maps over Err error', () => {
            const result = err('fail');
            const mapped = result.mapError((e) => `ERR: ${e}`);
            expect(mapped.getError()).toBe('ERR: fail');
        });
        it('skips mapError on Ok', () => {
            const result = ok(42);
            const mapped = result.mapError((e) => `ERR: ${e}`);
            expect(mapped.getValue()).toBe(42);
        });
    });
    describe('toString / instance checks', () => {
        it('Ok has _tag ok', () => {
            const result = ok(1);
            expect(result._tag).toBe('ok');
        });
        it('Err has _tag err', () => {
            const result = err('error');
            expect(result._tag).toBe('err');
        });
    });
});
//# sourceMappingURL=result.test.js.map