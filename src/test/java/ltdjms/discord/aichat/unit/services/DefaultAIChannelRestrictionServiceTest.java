package ltdjms.discord.aichat.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.aichat.persistence.AIChannelRestrictionRepository;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.aichat.services.DefaultAIChannelRestrictionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** 測試 {@link DefaultAIChannelRestrictionService}。 */
@DisplayName("DefaultAIChannelRestrictionService 測試")
class DefaultAIChannelRestrictionServiceTest {

  private AIChannelRestrictionRepository repository;
  private AIChannelRestrictionService service;

  @BeforeEach
  void setUp() {
    repository = mock(AIChannelRestrictionRepository.class);
    service = new DefaultAIChannelRestrictionService(repository);
  }

  @Nested
  @DisplayName("isChannelAllowed")
  class IsChannelAllowed {

    @Test
    @DisplayName("當允許清單為空時，應返回 true（無限制模式）")
    void shouldReturnTrueWhenEmpty() {
      when(repository.findByGuildId(123L)).thenReturn(Result.ok(Set.of()));

      boolean allowed = service.isChannelAllowed(123L, 1001L);

      assertTrue(allowed);
    }

    @Test
    @DisplayName("當頻道在允許清單中時，應返回 true")
    void shouldReturnTrueWhenInList() {
      Set<AllowedChannel> channels = Set.of(new AllowedChannel(1001L, "general"));
      when(repository.findByGuildId(123L)).thenReturn(Result.ok(channels));

      boolean allowed = service.isChannelAllowed(123L, 1001L);

      assertTrue(allowed);
    }

    @Test
    @DisplayName("當頻道不在允許清單中時，應返回 false")
    void shouldReturnFalseWhenNotInList() {
      Set<AllowedChannel> channels = Set.of(new AllowedChannel(1001L, "general"));
      when(repository.findByGuildId(123L)).thenReturn(Result.ok(channels));

      boolean allowed = service.isChannelAllowed(123L, 1002L);

      assertFalse(allowed);
    }

    @Test
    @DisplayName("當資料庫查詢失敗時，應返回 false")
    void shouldReturnFalseWhenQueryFails() {
      when(repository.findByGuildId(123L))
          .thenReturn(Result.err(DomainError.persistenceFailure("DB error", null)));

      boolean allowed = service.isChannelAllowed(123L, 1001L);

      assertFalse(allowed);
    }
  }

  @Nested
  @DisplayName("getAllowedChannels")
  class GetAllowedChannels {

    @Test
    @DisplayName("應成功返回允許頻道清單")
    void shouldReturnAllowedChannels() {
      Set<AllowedChannel> channels =
          Set.of(new AllowedChannel(1001L, "general"), new AllowedChannel(1002L, "ai-chat"));
      when(repository.findByGuildId(123L)).thenReturn(Result.ok(new HashSet<>(channels)));

      Result<Set<AllowedChannel>, DomainError> result = service.getAllowedChannels(123L);

      assertTrue(result.isOk());
      assertEquals(2, result.getValue().size());
    }
  }

  @Nested
  @DisplayName("addAllowedChannel")
  class AddAllowedChannel {

    @Test
    @DisplayName("應成功新增允許頻道")
    void shouldAddChannel() {
      AllowedChannel channel = new AllowedChannel(1001L, "general");
      when(repository.addChannel(123L, channel)).thenReturn(Result.ok(channel));

      Result<AllowedChannel, DomainError> result = service.addAllowedChannel(123L, channel);

      assertTrue(result.isOk());
      assertEquals(channel, result.getValue());
    }

    @Test
    @DisplayName("當頻道重複時，應返回 DUPLICATE_CHANNEL 錯誤")
    void shouldReturnErrorWhenDuplicate() {
      AllowedChannel channel = new AllowedChannel(1001L, "general");
      when(repository.addChannel(123L, channel))
          .thenReturn(
              Result.err(new DomainError(DomainError.Category.DUPLICATE_CHANNEL, "重複", null)));

      Result<AllowedChannel, DomainError> result = service.addAllowedChannel(123L, channel);

      assertTrue(result.isErr());
      assertEquals(DomainError.Category.DUPLICATE_CHANNEL, result.getError().category());
    }
  }

  @Nested
  @DisplayName("removeAllowedChannel")
  class RemoveAllowedChannel {

    @Test
    @DisplayName("應成功移除允許頻道")
    void shouldRemoveChannel() {
      when(repository.removeChannel(123L, 1001L)).thenReturn(Result.okVoid());

      Result<Unit, DomainError> result = service.removeAllowedChannel(123L, 1001L);

      assertTrue(result.isOk());
    }

    @Test
    @DisplayName("當頻道不存在時，應返回 CHANNEL_NOT_FOUND 錯誤")
    void shouldReturnErrorWhenNotFound() {
      when(repository.removeChannel(123L, 1001L))
          .thenReturn(
              Result.err(new DomainError(DomainError.Category.CHANNEL_NOT_FOUND, "不存在", null)));

      Result<Unit, DomainError> result = service.removeAllowedChannel(123L, 1001L);

      assertTrue(result.isErr());
      assertEquals(DomainError.Category.CHANNEL_NOT_FOUND, result.getError().category());
    }
  }
}
