package ltdjms.discord.shared;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for the Result type and its associated error types. */
class ResultTest {

  @Nested
  @DisplayName("Result.ok()")
  class OkTests {

    @Test
    @DisplayName("should create a success result with value")
    void shouldCreateSuccessResultWithValue() {
      Result<String, DomainError> result = Result.ok("hello");

      assertTrue(result.isOk());
      assertFalse(result.isErr());
      assertEquals("hello", result.getValue());
    }

    @Test
    @DisplayName("should throw when getting error from success result")
    void shouldThrowWhenGettingErrorFromSuccess() {
      Result<String, DomainError> result = Result.ok("hello");

      assertThrows(IllegalStateException.class, result::getError);
    }

    @Test
    @DisplayName("should map value for success result")
    void shouldMapValueForSuccess() {
      Result<Integer, DomainError> result = Result.ok(5);

      Result<String, DomainError> mapped = result.map(v -> "value: " + v);

      assertTrue(mapped.isOk());
      assertEquals("value: 5", mapped.getValue());
    }

    @Test
    @DisplayName("should flatMap value for success result")
    void shouldFlatMapValueForSuccess() {
      Result<Integer, DomainError> result = Result.ok(5);

      Result<String, DomainError> flatMapped = result.flatMap(v -> Result.ok("value: " + v));

      assertTrue(flatMapped.isOk());
      assertEquals("value: 5", flatMapped.getValue());
    }
  }

  @Nested
  @DisplayName("Result.err()")
  class ErrTests {

    @Test
    @DisplayName("should create an error result with error")
    void shouldCreateErrorResultWithError() {
      DomainError error = DomainError.invalidInput("bad input");
      Result<String, DomainError> result = Result.err(error);

      assertTrue(result.isErr());
      assertFalse(result.isOk());
      assertEquals(error, result.getError());
    }

    @Test
    @DisplayName("should throw when getting value from error result")
    void shouldThrowWhenGettingValueFromError() {
      Result<String, DomainError> result = Result.err(DomainError.invalidInput("bad"));

      assertThrows(IllegalStateException.class, result::getValue);
    }

    @Test
    @DisplayName("should not map value for error result")
    void shouldNotMapValueForError() {
      DomainError error = DomainError.invalidInput("bad");
      Result<Integer, DomainError> result = Result.err(error);

      Result<String, DomainError> mapped = result.map(v -> "value: " + v);

      assertTrue(mapped.isErr());
      assertEquals(error, mapped.getError());
    }

    @Test
    @DisplayName("should not flatMap value for error result")
    void shouldNotFlatMapValueForError() {
      DomainError error = DomainError.invalidInput("bad");
      Result<Integer, DomainError> result = Result.err(error);

      Result<String, DomainError> flatMapped = result.flatMap(v -> Result.ok("value: " + v));

      assertTrue(flatMapped.isErr());
      assertEquals(error, flatMapped.getError());
    }
  }

  @Nested
  @DisplayName("Result.getOrElse()")
  class GetOrElseTests {

    @Test
    @DisplayName("should return value for success result")
    void shouldReturnValueForSuccess() {
      Result<String, DomainError> result = Result.ok("hello");

      assertEquals("hello", result.getOrElse("default"));
    }

    @Test
    @DisplayName("should return default for error result")
    void shouldReturnDefaultForError() {
      Result<String, DomainError> result = Result.err(DomainError.invalidInput("bad"));

      assertEquals("default", result.getOrElse("default"));
    }
  }

  @Nested
  @DisplayName("Result.mapError()")
  class MapErrorTests {

    @Test
    @DisplayName("should map error for error result")
    void shouldMapErrorForErrorResult() {
      Result<String, DomainError> result = Result.err(DomainError.invalidInput("bad"));

      Result<String, String> mapped = result.mapError(e -> e.message());

      assertTrue(mapped.isErr());
      assertEquals("bad", mapped.getError());
    }

    @Test
    @DisplayName("should not map error for success result")
    void shouldNotMapErrorForSuccess() {
      Result<String, DomainError> result = Result.ok("hello");

      Result<String, String> mapped = result.mapError(e -> e.message());

      assertTrue(mapped.isOk());
      assertEquals("hello", mapped.getValue());
    }
  }

  @Nested
  @DisplayName("DomainError types")
  class DomainErrorTests {

    @Test
    @DisplayName("should create InvalidInput error")
    void shouldCreateInvalidInputError() {
      DomainError error = DomainError.invalidInput("invalid amount");

      assertEquals(DomainError.Category.INVALID_INPUT, error.category());
      assertEquals("invalid amount", error.message());
      assertNull(error.cause());
    }

    @Test
    @DisplayName("should create InsufficientBalance error")
    void shouldCreateInsufficientBalanceError() {
      DomainError error = DomainError.insufficientBalance("not enough coins");

      assertEquals(DomainError.Category.INSUFFICIENT_BALANCE, error.category());
      assertEquals("not enough coins", error.message());
    }

    @Test
    @DisplayName("should create InsufficientTokens error")
    void shouldCreateInsufficientTokensError() {
      DomainError error = DomainError.insufficientTokens("not enough tokens");

      assertEquals(DomainError.Category.INSUFFICIENT_TOKENS, error.category());
      assertEquals("not enough tokens", error.message());
    }

    @Test
    @DisplayName("should create PersistenceFailure error with cause")
    void shouldCreatePersistenceFailureWithCause() {
      RuntimeException cause = new RuntimeException("DB down");
      DomainError error = DomainError.persistenceFailure("failed to save", cause);

      assertEquals(DomainError.Category.PERSISTENCE_FAILURE, error.category());
      assertEquals("failed to save", error.message());
      assertEquals(cause, error.cause());
    }

    @Test
    @DisplayName("should create UnexpectedFailure error")
    void shouldCreateUnexpectedFailureError() {
      RuntimeException cause = new RuntimeException("oops");
      DomainError error = DomainError.unexpectedFailure("something went wrong", cause);

      assertEquals(DomainError.Category.UNEXPECTED_FAILURE, error.category());
      assertEquals("something went wrong", error.message());
      assertEquals(cause, error.cause());
    }
  }
}
