import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { GameConfigManagementFacade } from '../../../facades/GameConfigManagementFacade.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';

/**
 * Handler for game settings interactions (admin_game_*).
 * Supports game selection, view current config, edit via modal.
 */
export class GameSettingsHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_game';

  constructor(
    private readonly facade: GameConfigManagementFacade,
    sessionManager: AdminPanelSessionManager,
    errorHandler: BotErrorHandler,
  ) {
    super(sessionManager, errorHandler);
  }

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Permission check
    if (!this.checkAdminPermission(interaction)) {
      await interaction.reply(ZhTwStrings.permissionAdminRequired);
      return;
    }

    const session = this.getSession(interaction);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await this.ensureDeferred(interaction);

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.GAME_CONFIG);

    const fullCustomId = interaction.getCustomId();

    // Branch on sub-action
    if (fullCustomId === 'admin_game_edit_1') {
      // TODO: show modal for Dice Game 1 config
      await this.showDiceGameConfig(interaction, guildId, '1');
      return;
    }
    if (fullCustomId === 'admin_game_edit_2') {
      // TODO: show modal for Dice Game 2 config
      await this.showDiceGameConfig(interaction, guildId, '2');
      return;
    }

    // Default: show game settings overview
    await this.showGameOverview(interaction, guildId);
  }

  private async showDiceGameConfig(
    interaction: DiscordInteraction,
    guildId: string,
    gameNumber: '1' | '2',
  ): Promise<void> {
    if (gameNumber === '1') {
      const configResult = await this.facade.getDiceGame1Config(guildId);
      if (configResult.isOk()) {
        const cfg = configResult.getValue();
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.gameDice1Title)
          .setDescription(
            ZhTwStrings.gameDice1Fields
              .replace('{min}', String(cfg.minTokensPerPlay))
              .replace('{max}', String(cfg.maxTokensPerPlay))
              .replace('{reward}', String(cfg.rewardPerDiceValue)),
          )
          .setColor(0xFEE75C);
        await interaction.editEmbed(embed);
      }
    } else {
      const configResult = await this.facade.getDiceGame2Config(guildId);
      if (configResult.isOk()) {
        const cfg = configResult.getValue();
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.gameDice2Title)
          .setDescription(
            ZhTwStrings.gameDice2Fields
              .replace('{min}', String(cfg.minTokensPerPlay))
              .replace('{max}', String(cfg.maxTokensPerPlay))
              .replace('{straight}', String(cfg.straightMultiplier))
              .replace('{base}', String(cfg.baseMultiplier))
              .replace('{lowTriple}', String(cfg.tripleLowBonus))
              .replace('{highTriple}', String(cfg.tripleHighBonus)),
          )
          .setColor(0xFEE75C);
        await interaction.editEmbed(embed);
      }
    }
  }

  private async showGameOverview(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const dice1Result = await this.facade.getDiceGame1Config(guildId);
    const dice2Result = await this.facade.getDiceGame2Config(guildId);

    const descriptionLines: string[] = [];
    descriptionLines.push(`**${ZhTwStrings.gameDiceGame1}**`);

    if (dice1Result.isOk()) {
      const cfg = dice1Result.getValue();
      descriptionLines.push(
        ZhTwStrings.gameDice1Fields
          .replace('{min}', String(cfg.minTokensPerPlay))
          .replace('{max}', String(cfg.maxTokensPerPlay))
          .replace('{reward}', String(cfg.rewardPerDiceValue)),
      );
    } else {
      descriptionLines.push('尚未設定');
    }

    descriptionLines.push('');
    descriptionLines.push(`**${ZhTwStrings.gameDiceGame2}**`);

    if (dice2Result.isOk()) {
      const cfg = dice2Result.getValue();
      descriptionLines.push(
        ZhTwStrings.gameDice2Fields
          .replace('{min}', String(cfg.minTokensPerPlay))
          .replace('{max}', String(cfg.maxTokensPerPlay))
          .replace('{straight}', String(cfg.straightMultiplier))
          .replace('{base}', String(cfg.baseMultiplier))
          .replace('{lowTriple}', String(cfg.tripleLowBonus))
          .replace('{highTriple}', String(cfg.tripleHighBonus)),
      );
    } else {
      descriptionLines.push('尚未設定');
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.gameSelectTitle)
      .setDescription(descriptionLines.join('\n'))
      .setColor(0xFEE75C);
    await interaction.editEmbed(embed);
  }
}
