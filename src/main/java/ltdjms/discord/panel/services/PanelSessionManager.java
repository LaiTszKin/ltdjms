package ltdjms.discord.panel.services;

import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Manages active user panel sessions to allow real-time updates.
 */
public class PanelSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(PanelSessionManager.class);
    // 15 minutes TTL (interaction tokens expire after 15 mins)
    private static final long TTL_SECONDS = 15 * 60;

    private final Map<String, PanelSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a user panel session.
     *
     * @param guildId     the Discord guild ID
     * @param userId      the Discord user ID
     * @param hook        the interaction hook for updating the message
     * @param userMention the mention string of the user (e.g. <@123...>)
     */
    public void registerSession(long guildId, long userId, InteractionHook hook, String userMention) {
        String key = getKey(guildId, userId);
        sessions.put(key, new PanelSession(hook, userMention, Instant.now()));
        LOG.debug("Registered panel session for key={}", key);
    }

    /**
     * Updates the panel for a user if a valid session exists.
     *
     * @param guildId  the Discord guild ID
     * @param userId   the Discord user ID
     * @param consumer action to perform on the InteractionHook and user mention
     */
    public void updatePanel(long guildId, long userId, BiConsumer<InteractionHook, String> consumer) {
        String key = getKey(guildId, userId);
        PanelSession session = sessions.get(key);
        if (session != null) {
            if (isExpired(session)) {
                sessions.remove(key);
                LOG.debug("Removed expired session for key={}", key);
            } else {
                try {
                    consumer.accept(session.hook(), session.userMention());
                } catch (Exception e) {
                    LOG.warn("Failed to update session for key={}. Removing session.", key, e);
                    sessions.remove(key);
                }
            }
        }
    }

    private boolean isExpired(PanelSession session) {
        return Instant.now().isAfter(session.createdAt().plusSeconds(TTL_SECONDS));
    }

    private String getKey(long guildId, long userId) {
        return guildId + ":" + userId;
    }

    private record PanelSession(InteractionHook hook, String userMention, Instant createdAt) {}
}
