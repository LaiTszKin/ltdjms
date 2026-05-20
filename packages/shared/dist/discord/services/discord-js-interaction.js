import { CommandInteraction, } from 'discord.js';
/**
 * Discord.js implementation of DiscordInteraction.
 * Wraps CommandInteraction / ButtonInteraction / ModalSubmitInteraction.
 */
export class DiscordJsInteraction {
    interaction;
    acknowledged;
    _ephemeral;
    constructor(interaction, ephemeral) {
        this.interaction = interaction;
        this.acknowledged = interaction.replied || interaction.deferred;
        // CommandInteraction has an ephemeral property; others default to false
        this._ephemeral = ephemeral ?? (interaction instanceof CommandInteraction ? (interaction.ephemeral ?? false) : false);
    }
    getGuildId() {
        return this.interaction.guildId ?? '0';
    }
    getUserId() {
        return this.interaction.user.id;
    }
    isEphemeral() {
        return this._ephemeral;
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