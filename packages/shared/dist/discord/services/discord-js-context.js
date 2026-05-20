/**
 * Discord.js implementation of DiscordContext.
 * Wraps a discord.js interaction to provide guild/user/channel context.
 */
export class DiscordJsContext {
    interaction;
    constructor(interaction) {
        this.interaction = interaction;
    }
    getGuildId() {
        return this.interaction.guildId ? Number(this.interaction.guildId) : 0;
    }
    getUserId() {
        return Number(this.interaction.user.id);
    }
    getChannelId() {
        return this.interaction.channelId
            ? Number(this.interaction.channelId)
            : 0;
    }
    getUserMention() {
        return `<@${this.interaction.user.id}>`;
    }
    getOption(name) {
        if (!('options' in this.interaction)) {
            return null;
        }
        const option = this.interaction.options.get
            ? this.interaction.options.get(name)
            : null;
        return option?.value?.toString() ?? null;
    }
    getOptionAsString(name) {
        if (!('options' in this.interaction)) {
            return null;
        }
        const option = this.interaction.options.get(name);
        if (option && typeof option.value === 'string') {
            return option.value;
        }
        return null;
    }
    getOptionAsNumber(name) {
        if (!('options' in this.interaction)) {
            return null;
        }
        const option = this.interaction.options.get(name);
        if (option && typeof option.value === 'number') {
            return option.value;
        }
        return null;
    }
}
//# sourceMappingURL=discord-js-context.js.map