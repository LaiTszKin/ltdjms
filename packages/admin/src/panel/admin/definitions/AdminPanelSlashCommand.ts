/**
 * Slash command definition for /admin-panel.
 * Used by SlashCommandRegistrar for Discord API registration.
 */
export const AdminPanelSlashCommand = {
  name: 'admin-panel',
  description: '開啟管理面板',
  defaultMemberPermissions: '8', // ADMINISTRATOR permission
  nameLocalizations: {
    'zh-TW': '管理面板',
  },
  descriptionLocalizations: {
    'zh-TW': '開啟管理面板，管理貨幣、代幣、遊戲、產品、AI 等設定',
  },
};
