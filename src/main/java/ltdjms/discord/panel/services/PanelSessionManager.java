package ltdjms.discord.panel.services;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.interactions.InteractionHook;

/** Manages active user panel sessions to allow real-time updates. */
public class PanelSessionManager {

  /** Data class holding session context for panel updates. */
  public record SessionContext(InteractionHook hook, String userMention, long userId) {}

  /** Session context including guild ID for cross-guild user updates. */
  public record UserSessionContext(
      InteractionHook hook, String userMention, long guildId, long userId) {}

  private static final Logger LOG = LoggerFactory.getLogger(PanelSessionManager.class);
  // 15 minutes TTL (interaction tokens expire after 15 mins)
  private static final long TTL_SECONDS = 15 * 60;

  private final Map<String, PanelSession> sessions = new ConcurrentHashMap<>();
  private final Map<Long, Set<String>> sessionKeysByUser = new ConcurrentHashMap<>();

  /**
   * Registers a user panel session.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param hook the interaction hook for updating the message
   * @param userMention the mention string of the user (e.g. <@123...>)
   */
  public void registerSession(long guildId, long userId, InteractionHook hook, String userMention) {
    String key = getKey(guildId, userId);
    sessions.put(key, new PanelSession(hook, userMention, Instant.now()));
    sessionKeysByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(key);
    LOG.debug("Registered panel session for key={}", key);
  }

  /**
   * Updates the panel for a user if a valid session exists.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param consumer action to perform on the InteractionHook and user mention
   */
  public void updatePanel(long guildId, long userId, BiConsumer<InteractionHook, String> consumer) {
    String key = getKey(guildId, userId);
    PanelSession session = sessions.get(key);
    if (session != null) {
      if (isExpired(session)) {
        removeSession(key, userId);
        LOG.debug("Removed expired session for key={}", key);
      } else {
        try {
          consumer.accept(session.hook(), session.userMention());
        } catch (Exception e) {
          LOG.warn("Failed to update session for key={}. Removing session.", key, e);
          removeSession(key, userId);
        }
      }
    }
  }

  /**
   * Updates all panels for a guild. Used when guild-wide settings change (e.g., currency name/icon
   * changes).
   *
   * @param guildId the Discord guild ID
   * @param consumer action to perform on each InteractionHook and user mention
   */
  public void updatePanelsByGuild(long guildId, BiConsumer<InteractionHook, String> consumer) {
    String guildPrefix = guildId + ":";
    sessions
        .entrySet()
        .removeIf(
            entry -> {
              String key = entry.getKey();
              if (!key.startsWith(guildPrefix)) {
                return false;
              }
              PanelSession session = entry.getValue();
              if (isExpired(session)) {
                removeSessionKey(key);
                LOG.debug("Removed expired session for key={}", key);
                return true;
              }
              try {
                consumer.accept(session.hook(), session.userMention());
              } catch (Exception e) {
                LOG.warn("Failed to update session for key={}. Removing session.", key, e);
                removeSessionKey(key);
                return true;
              }
              return false;
            });
  }

  /**
   * Updates all panels for a guild with full session context (including userId). Used when
   * guild-wide settings change and the updater needs user IDs.
   *
   * @param guildId the Discord guild ID
   * @param consumer action to perform on each session context
   */
  public void updatePanelsByGuildWithContext(long guildId, Consumer<SessionContext> consumer) {
    String guildPrefix = guildId + ":";
    sessions
        .entrySet()
        .removeIf(
            entry -> {
              String key = entry.getKey();
              if (!key.startsWith(guildPrefix)) {
                return false;
              }
              PanelSession session = entry.getValue();
              if (isExpired(session)) {
                removeSessionKey(key);
                LOG.debug("Removed expired session for key={}", key);
                return true;
              }
              try {
                long userId = Long.parseLong(key.substring(guildPrefix.length()));
                consumer.accept(new SessionContext(session.hook(), session.userMention(), userId));
              } catch (Exception e) {
                LOG.warn("Failed to update session for key={}. Removing session.", key, e);
                removeSessionKey(key);
                return true;
              }
              return false;
            });
  }

  /**
   * Updates all open panels for a user across guilds. Used when global membership state changes.
   *
   * @param userId Discord user ID
   * @param consumer action to perform on each matching session
   */
  public void updatePanelsByUser(long userId, Consumer<UserSessionContext> consumer) {
    Set<String> keys = sessionKeysByUser.getOrDefault(userId, Set.of());
    for (String key : Set.copyOf(keys)) {
      PanelSession session = sessions.get(key);
      if (session == null) {
        removeSessionKey(key);
        continue;
      }
      if (isExpired(session)) {
        removeSession(key, userId);
        LOG.debug("Removed expired session for key={}", key);
        continue;
      }
      try {
        int separator = key.indexOf(':');
        long guildId = Long.parseLong(key.substring(0, separator));
        consumer.accept(
            new UserSessionContext(session.hook(), session.userMention(), guildId, userId));
      } catch (Exception e) {
        LOG.warn("Failed to update session for key={}. Removing session.", key, e);
        removeSession(key, userId);
      }
    }
  }

  private boolean isExpired(PanelSession session) {
    return Instant.now().isAfter(session.createdAt().plusSeconds(TTL_SECONDS));
  }

  private void removeSession(String key, long userId) {
    sessions.remove(key);
    Set<String> keys = sessionKeysByUser.get(userId);
    if (keys != null) {
      keys.remove(key);
      if (keys.isEmpty()) {
        sessionKeysByUser.remove(userId, keys);
      }
    }
  }

  private void removeSessionKey(String key) {
    sessions.remove(key);
    int separator = key.indexOf(':');
    if (separator <= 0) {
      return;
    }
    try {
      long userId = Long.parseLong(key.substring(separator + 1));
      Set<String> keys = sessionKeysByUser.get(userId);
      if (keys != null) {
        keys.remove(key);
        if (keys.isEmpty()) {
          sessionKeysByUser.remove(userId, keys);
        }
      }
    } catch (NumberFormatException ignored) {
      // Ignore malformed keys.
    }
  }

  private String getKey(long guildId, long userId) {
    return guildId + ":" + userId;
  }

  private record PanelSession(InteractionHook hook, String userMention, Instant createdAt) {}
}
