package ltdjms.discord.shared;

import java.util.Objects;
import java.util.function.Function;

/**
 * A Result type that represents either a success value or an error.
 * Inspired by Rust's Result<T, E> for explicit error handling.
 *
 * @param <T> the success value type
 * @param <E> the error type
 */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    /**
     * Creates a success result with the given value.
     */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Creates a success result with a Unit value for void operations.
     * Use this when the success case doesn't need to carry a meaningful value.
     */
    @SuppressWarnings("unchecked")
    static <E> Result<Unit, E> okVoid() {
        return (Result<Unit, E>) Ok.UNIT_OK;
    }

    /**
     * Creates an error result with the given error.
     */
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    /**
     * Returns true if this is a success result.
     */
    boolean isOk();

    /**
     * Returns true if this is an error result.
     */
    boolean isErr();

    /**
     * Returns the success value.
     * @throws IllegalStateException if this is an error result
     */
    T getValue();

    /**
     * Returns the error.
     * @throws IllegalStateException if this is a success result
     */
    E getError();

    /**
     * Returns the success value or the given default if this is an error.
     */
    T getOrElse(T defaultValue);

    /**
     * Maps the success value using the given function.
     * If this is an error, returns the error unchanged.
     */
    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);

    /**
     * Maps the success value using a function that returns a Result.
     * If this is an error, returns the error unchanged.
     */
    <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper);

    /**
     * Maps the error using the given function.
     * If this is a success, returns the success value unchanged.
     */
    <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper);

    /**
     * Success case implementation.
     */
    record Ok<T, E>(T value) implements Result<T, E> {

        /** Singleton Ok result for Unit (void) operations. */
        static final Ok<Unit, ?> UNIT_OK = new Ok<>(Unit.INSTANCE);

        public Ok {
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public E getError() {
            throw new IllegalStateException("Cannot get error from Ok result");
        }

        @Override
        public T getOrElse(T defaultValue) {
            return value;
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return new Ok<>(mapper.apply(value));
        }

        @Override
        public <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper) {
            return mapper.apply(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
            return (Result<T, F>) this;
        }
    }

    /**
     * Error case implementation.
     */
    record Err<T, E>(E error) implements Result<T, E> {

        public Err {
            Objects.requireNonNull(error, "error must not be null");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        public T getValue() {
            throw new IllegalStateException("Cannot get value from Err result");
        }

        @Override
        public E getError() {
            return error;
        }

        @Override
        public T getOrElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return (Result<U, E>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper) {
            return (Result<U, E>) this;
        }

        @Override
        public <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
            return new Err<>(mapper.apply(error));
        }
    }
}
