/**
 * Discord.js implementation of DiscordInteraction.
 * Wraps CommandInteraction / ButtonInteraction / ModalSubmitInteraction.
 */
export class DiscordJsInteraction {
    interaction;
    acknowledged;
    constructor(interaction) {
        this.interaction = interaction;
        this.acknowledged = interaction.replied || interaction.deferred;
    }
    getGuildId() {
        return this.interaction.guildId ? Number(this.interaction.guildId) : 0;
    }
    getUserId() {
        return Number(this.interaction.user.id);
    }
    getChannelId() {
        return this.interaction.channelId ?? '0';
    }
    isEphemeral() {
        return false;
    }
    async reply(message) {
        if (this.acknowledged) {
            await this.interaction.followUp(message);
        }
        else {
            await this.interaction.reply(message);
        }
        this.acknowledged = true;
    }
    async replyEmbed(embed) {
        const discordEmbed = embed;
        if (this.acknowledged) {
            await this.interaction.followUp({ embeds: [discordEmbed] });
        }
        else {
            await this.interaction.reply({ embeds: [discordEmbed] });
        }
        this.acknowledged = true;
    }
    async editEmbed(embed) {
        const discordEmbed = embed;
        await this.interaction.editReply({ embeds: [discordEmbed] });
    }
    async deferReply() {
        if (!this.acknowledged) {
            await this.interaction.deferReply();
            this.acknowledged = true;
        }
    }
    getHook() {
        return this.interaction;
    }
    isAcknowledged() {
        return this.acknowledged;
    }
}
//# sourceMappingURL=discord-js-interaction.js.map