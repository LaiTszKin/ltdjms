/**
 * A Result type that represents either a success value or an error.
 * Inspired by Rust's Result<T, E> for explicit error handling.
 */

export type Result<T, E> = Ok<T, E> | Err<T, E>;

export class Ok<T, E> {
  readonly _tag: 'ok' = 'ok';
  constructor(readonly value: T) {
    if (value === null || value === undefined) {
      throw new Error('value must not be null or undefined');
    }
  }

  isOk(): this is Ok<T, E> {
    return true;
  }

  isErr(): this is Err<T, E> {
    return false;
  }

  getValue(): T {
    return this.value;
  }

  getError(): E {
    throw new Error('Cannot get error from Ok');
  }

  getOrElse(_defaultValue: T): T {
    return this.value;
  }

  map<U>(fn: (value: T) => U): Result<U, E> {
    return ok(fn(this.value));
  }

  flatMap<U>(fn: (value: T) => Result<U, E>): Result<U, E> {
    return fn(this.value);
  }

  mapError<F>(_fn: (error: E) => F): Result<T, F> {
    return this as unknown as Result<T, F>;
  }
}

export class Err<T, E> {
  readonly _tag: 'err' = 'err';
  constructor(readonly error: E) {
    if (error === null || error === undefined) {
      throw new Error('error must not be null or undefined');
    }
  }

  isOk(): this is Ok<T, E> {
    return false;
  }

  isErr(): this is Err<T, E> {
    return true;
  }

  getValue(): T {
    throw new Error('Cannot get value from Err');
  }

  getError(): E {
    return this.error;
  }

  getOrElse(defaultValue: T): T {
    return defaultValue;
  }

  map<U>(_fn: (value: T) => U): Result<U, E> {
    return this as unknown as Result<U, E>;
  }

  flatMap<U>(_fn: (value: T) => Result<U, E>): Result<U, E> {
    return this as unknown as Result<U, E>;
  }

  mapError<F>(fn: (error: E) => F): Result<T, F> {
    return new Err<T, F>(fn(this.error));
  }
}

/** A unit type representing the absence of a meaningful value. */
export class Unit {
  static readonly INSTANCE = new Unit();
  private constructor() {}
  toString(): string {
    return 'Unit';
  }
}

/** Creates a success result with the given value. */
export function ok<T, E>(value: T): Result<T, E> {
  return new Ok(value);
}

/** Creates a success result with a Unit value for void operations. */
export function okVoid<E>(): Result<Unit, E> {
  return new Ok<Unit, E>(Unit.INSTANCE);
}

/** Creates an error result with the given error. */
export function err<T, E>(error: E): Result<T, E> {
  return new Err(error);
}

/** Type guard for Ok variant. */
export function isOk<T, E>(result: Result<T, E>): result is Ok<T, E> {
  return result._tag === 'ok';
}

/** Type guard for Err variant. */
export function isErr<T, E>(result: Result<T, E>): result is Err<T, E> {
  return result._tag === 'err';
}
