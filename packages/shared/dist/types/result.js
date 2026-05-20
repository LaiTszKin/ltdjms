/**
 * A Result type that represents either a success value or an error.
 * Inspired by Rust's Result<T, E> for explicit error handling.
 */
export class Ok {
    value;
    _tag = 'ok';
    constructor(value) {
        this.value = value;
        if (value === null || value === undefined) {
            throw new Error('value must not be null or undefined');
        }
    }
    isOk() {
        return true;
    }
    isErr() {
        return false;
    }
    getValue() {
        return this.value;
    }
    getError() {
        throw new Error('Cannot get error from Ok');
    }
    getOrElse(_defaultValue) {
        return this.value;
    }
    map(fn) {
        return ok(fn(this.value));
    }
    flatMap(fn) {
        return fn(this.value);
    }
    mapError(_fn) {
        return new Ok(this.value);
    }
}
export class Err {
    error;
    _tag = 'err';
    constructor(error) {
        this.error = error;
        if (error === null || error === undefined) {
            throw new Error('error must not be null or undefined');
        }
    }
    isOk() {
        return false;
    }
    isErr() {
        return true;
    }
    getValue() {
        throw new Error('Cannot get value from Err');
    }
    getError() {
        return this.error;
    }
    getOrElse(defaultValue) {
        return defaultValue;
    }
    map(_fn) {
        return new Err(this.error);
    }
    flatMap(_fn) {
        return new Err(this.error);
    }
    mapError(fn) {
        return new Err(fn(this.error));
    }
}
/** A unit type representing the absence of a meaningful value. */
export class Unit {
    static INSTANCE = new Unit();
    constructor() { }
    toString() {
        return 'Unit';
    }
}
/** Creates a success result with the given value. */
export function ok(value) {
    return new Ok(value);
}
/** Creates a success result with a Unit value for void operations. */
export function okVoid() {
    return new Ok(Unit.INSTANCE);
}
/** Creates an error result with the given error. */
export function err(error) {
    return new Err(error);
}
/** Type guard for Ok variant. */
export function isOk(result) {
    return result._tag === 'ok';
}
/** Type guard for Err variant. */
export function isErr(result) {
    return result._tag === 'err';
}
//# sourceMappingURL=result.js.map