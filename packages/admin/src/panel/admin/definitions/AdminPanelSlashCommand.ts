import { CommandLocalizations } from '@ltdjms/shared';

/**
 * Slash command definition for /admin-panel.
 * Used by SlashCommandRegistrar for Discord API registration.
 */
export const AdminPanelSlashCommand = {
  name: 'admin-panel',
  description: '開啟管理面板',
  defaultMemberPermissions: '8', // ADMINISTRATOR permission
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['admin-panel'],
  descriptionLocalizations: CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['admin-panel'],
};
