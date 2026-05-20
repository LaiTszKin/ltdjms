import type { SlashCommandDefinition } from './SlashCommandRegistrar.js';

/** /dispatch-panel — open the dispatch management panel (admin only). */
export const DispatchPanelSlashCommand: SlashCommandDefinition = {
  name: 'dispatch-panel',
  description: '開啟派單管理面板',
  defaultMemberPermissions: '8',
  nameLocalizations: { 'zh-TW': '派單面板' },
  descriptionLocalizations: { 'zh-TW': '開啟護航派單管理面板（管理員專用）' },
};
