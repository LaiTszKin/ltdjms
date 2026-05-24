import { CommandLocalizations } from '@ltdjms/shared';

/**
 * Slash command definition for /user-panel.
 * Used by SlashCommandRegistrar for Discord API registration.
 */
export const UserPanelSlashCommand = {
  name: 'user-panel',
  description: '開啟用戶面板',
  defaultMemberPermissions: null,
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['user-panel'],
  descriptionLocalizations: CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['user-panel'],
};
