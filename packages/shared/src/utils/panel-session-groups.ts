/**
 * Groups panel sessions by channel for batched Discord API updates.
 * Shared by admin and user panel update listeners.
 */
export function groupSessionsByChannel<T extends { channelId?: string; messageId?: string }>(
  sessions: T[],
): Map<string, T[]> {
  const channelGroupMap = new Map<string, T[]>();

  for (const session of sessions) {
    if (!session.channelId || !session.messageId) continue;
    const group = channelGroupMap.get(session.channelId);
    if (group) {
      group.push(session);
    } else {
      channelGroupMap.set(session.channelId, [session]);
    }
  }

  return channelGroupMap;
}
