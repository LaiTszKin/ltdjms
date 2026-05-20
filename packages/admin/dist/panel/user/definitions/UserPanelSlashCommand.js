/**
 * Slash command definition for /user-panel.
 * Used by SlashCommandRegistrar for Discord API registration.
 */
export const UserPanelSlashCommand = {
    name: 'user-panel',
    description: '開啟用戶面板',
    defaultMemberPermissions: null, // Available to all members
    nameLocalizations: {
        'zh-TW': '用戶面板',
    },
    descriptionLocalizations: {
        'zh-TW': '查看餘額、代幣、交易記錄與兌換兌換碼',
    },
};
//# sourceMappingURL=UserPanelSlashCommand.js.map