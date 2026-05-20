/**
 * A Result type that represents either a success value or an error.
 * Inspired by Rust's Result<T, E> for explicit error handling.
 */
export type Result<T, E> = Ok<T, E> | Err<T, E>;
export declare class Ok<T, E> {
    readonly value: T;
    readonly _tag: 'ok';
    constructor(value: T);
    isOk(): this is Ok<T, E>;
    isErr(): this is Err<T, E>;
    getValue(): T;
    getError(): E;
    getOrElse(_defaultValue: T): T;
    map<U>(fn: (value: T) => U): Result<U, E>;
    flatMap<U>(fn: (value: T) => Result<U, E>): Result<U, E>;
    mapError<F>(_fn: (error: E) => F): Result<T, F>;
}
export declare class Err<T, E> {
    readonly error: E;
    readonly _tag: 'err';
    constructor(error: E);
    isOk(): this is Ok<T, E>;
    isErr(): this is Err<T, E>;
    getValue(): T;
    getError(): E;
    getOrElse(defaultValue: T): T;
    map<U>(_fn: (value: T) => U): Result<U, E>;
    flatMap<U>(_fn: (value: T) => Result<U, E>): Result<U, E>;
    mapError<F>(fn: (error: E) => F): Result<T, F>;
}
/** A unit type representing the absence of a meaningful value. */
export declare class Unit {
    static readonly INSTANCE: Unit;
    private constructor();
    toString(): string;
}
/** Creates a success result with the given value. */
export declare function ok<T, E>(value: T): Result<T, E>;
/** Creates a success result with a Unit value for void operations. */
export declare function okVoid<E>(): Result<Unit, E>;
/** Creates an error result with the given error. */
export declare function err<T, E>(error: E): Result<T, E>;
/** Type guard for Ok variant. */
export declare function isOk<T, E>(result: Result<T, E>): result is Ok<T, E>;
/** Type guard for Err variant. */
export declare function isErr<T, E>(result: Result<T, E>): result is Err<T, E>;
