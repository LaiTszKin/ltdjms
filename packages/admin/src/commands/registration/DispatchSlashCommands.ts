import { CommandLocalizations } from '@ltdjms/shared';
import type { SlashCommandDefinition } from './SlashCommandRegistrar.js';

/** /dispatch-panel — open the dispatch management panel (admin only). */
export const DispatchPanelSlashCommand: SlashCommandDefinition = {
  name: 'dispatch-panel',
  description: '開啟派單管理面板',
  defaultMemberPermissions: '8',
  nameLocalizations: CommandLocalizations.COMMAND_NAME_LOCALIZATIONS['dispatch-panel'],
  descriptionLocalizations:
    CommandLocalizations.COMMAND_DESCRIPTION_LOCALIZATIONS['dispatch-panel'],
};
