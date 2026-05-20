import { z } from 'zod';
export const ManageMessageParamsSchema = z.object({
    messageId: z.string(),
    action: z.enum(['pin', 'delete', 'edit']),
    channelId: z.string().optional(),
    newContent: z.string().optional(),
    editMode: z.enum(['replace', 'append', 'prepend']).optional(),
});
/**
 * Manages messages: pin, delete, or edit.
 * Tool name: manage_message
 */
export class ManageMessageTool {
    authGuard;
    name = 'manage_message';
    description = '管理訊息（釘選/刪除/編輯）';
    schema = ManageMessageParamsSchema;
    constructor(authGuard) {
        this.authGuard = authGuard;
    }
    async execute(params, guild) {
        const authError = await this.authGuard.validateAdministrator(guild, this.name);
        if (authError)
            return authError;
        try {
            const { messageId, action, channelId, newContent, editMode } = params;
            // Find the message across channels
            const channelsToSearch = channelId
                ? [guild.channels.cache.get(channelId)].filter(Boolean)
                : guild.channels.cache.filter((c) => c.isTextBased()).values();
            for (const channel of channelsToSearch) {
                if (!channel || !channel.isTextBased() || !channel.isSendable())
                    continue;
                try {
                    const message = await channel.messages.fetch(messageId);
                    if (!message)
                        continue;
                    switch (action) {
                        case 'pin':
                            if (message.pinned) {
                                return '該訊息已被釘選。';
                            }
                            await message.pin('透過 AI Agent 釘選訊息');
                            return '已成功釘選訊息。';
                        case 'delete':
                            await message.delete();
                            return '已成功刪除訊息。';
                        case 'edit':
                            if (!newContent) {
                                return '編輯訊息需要提供新內容。';
                            }
                            let finalContent = newContent;
                            if (editMode === 'append') {
                                finalContent = message.content + '\n' + newContent;
                            }
                            else if (editMode === 'prepend') {
                                finalContent = newContent + '\n' + message.content;
                            }
                            await message.edit(finalContent);
                            return '已成功編輯訊息。';
                    }
                }
                catch {
                    continue;
                }
            }
            return `找不到訊息 ${messageId} 或無權限執行 ${action} 操作。`;
        }
        catch (error) {
            return `管理訊息失敗：${error instanceof Error ? error.message : String(error)}`;
        }
    }
}
//# sourceMappingURL=ManageMessageTool.js.map