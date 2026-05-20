import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
  ActionRowBuilder,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { GameConfigManagementFacade } from '../../../facades/GameConfigManagementFacade.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { Colors } from '../../../constants/colors.js';

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
      await this.showDiceGameModal(interaction, guildId, '1');
      return;
    }
    if (fullCustomId === 'admin_game_edit_2') {
      await this.showDiceGameModal(interaction, guildId, '2');
      return;
    }
    if (fullCustomId === 'admin_game_save_1') {
      await this.saveDiceGameConfig(interaction, guildId, '1');
      return;
    }
    if (fullCustomId === 'admin_game_save_2') {
      await this.saveDiceGameConfig(interaction, guildId, '2');
      return;
    }

    // Default: show game settings overview
    await this.showGameOverview(interaction, guildId);
  }

  private async showDiceGameModal(
    interaction: DiscordInteraction,
    guildId: string,
    gameNumber: '1' | '2',
  ): Promise<void> {
    if (gameNumber === '1') {
      const configResult = await this.facade.getDiceGame1Config(guildId);
      if (configResult.isErr()) {
        await this.errorHandler.handle(configResult.getError(), interaction);
        return;
      }
      const cfg = configResult.getValue();

      const modal = new ModalBuilder()
        .setCustomId('admin_game_save_1')
        .setTitle(ZhTwStrings.gameModalTitleDice1);

      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('minTokensPerPlay')
            .setLabel(ZhTwStrings.gameModalMin)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.minTokensPerPlay))
            .setMinLength(1).setMaxLength(20).setRequired(true),
        ),
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('maxTokensPerPlay')
            .setLabel(ZhTwStrings.gameModalMax)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.maxTokensPerPlay))
            .setMinLength(1).setMaxLength(20).setRequired(true),
        ),
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('rewardPerDiceValue')
            .setLabel(ZhTwStrings.gameModalReward)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.rewardPerDiceValue))
            .setMinLength(1).setMaxLength(20).setRequired(true),
        ),
      );

      const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
      await raw.showModal(modal);
    } else {
      const configResult = await this.facade.getDiceGame2Config(guildId);
      if (configResult.isErr()) {
        await this.errorHandler.handle(configResult.getError(), interaction);
        return;
      }
      const cfg = configResult.getValue();

      const modal = new ModalBuilder()
        .setCustomId('admin_game_save_2')
        .setTitle(ZhTwStrings.gameModalTitleDice2);

      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('minTokensPerPlay')
            .setLabel(ZhTwStrings.gameModalMin)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.minTokensPerPlay))
            .setMinLength(1).setMaxLength(20).setRequired(true),
        ),
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('maxTokensPerPlay')
            .setLabel(ZhTwStrings.gameModalMax)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.maxTokensPerPlay))
            .setMinLength(1).setMaxLength(20).setRequired(true),
        ),
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('straightMultiplier')
            .setLabel(ZhTwStrings.gameModalStraightMul)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.straightMultiplier))
            .setMinLength(1).setMaxLength(10).setRequired(true),
        ),
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('baseMultiplier')
            .setLabel(ZhTwStrings.gameModalBaseMul)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.baseMultiplier))
            .setMinLength(1).setMaxLength(10).setRequired(true),
        ),
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('tripleLowBonus')
            .setLabel(ZhTwStrings.gameModalTripleLow)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.tripleLowBonus))
            .setMinLength(1).setMaxLength(10).setRequired(true),
        ),
      );

      // Add the 6th field in a separate modal call stack
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('tripleHighBonus')
            .setLabel(ZhTwStrings.gameModalTripleHigh)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.tripleHighBonus))
            .setMinLength(1).setMaxLength(10).setRequired(true),
        ),
      );

      const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
      await raw.showModal(modal);
    }
  }

  private async saveDiceGameConfig(
    interaction: DiscordInteraction,
    guildId: string,
    gameNumber: '1' | '2',
  ): Promise<void> {
    const raw = interaction.getHook() as {
      fields: { getTextInputValue: (id: string) => string };
    };

    if (gameNumber === '1') {
      const min = parseInt(raw.fields.getTextInputValue('minTokensPerPlay'), 10);
      const max = parseInt(raw.fields.getTextInputValue('maxTokensPerPlay'), 10);
      const reward = parseInt(raw.fields.getTextInputValue('rewardPerDiceValue'), 10);

      if (isNaN(min) || isNaN(max) || isNaN(reward)) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.gameDice1Title)
          .setDescription(ZhTwStrings.gameValidationPositive)
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      const result = await this.facade.updateDiceGame1Config(guildId, {
        minTokensPerPlay: min,
        maxTokensPerPlay: max,
        rewardPerDiceValue: reward,
      });

      if (result.isOk()) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.gameDice1Title)
          .setDescription(ZhTwStrings.gameSaveSuccess)
          .setColor(Colors.SUCCESS);
        await interaction.editEmbed(embed);
      } else {
        await this.errorHandler.handle(result.getError(), interaction);
      }
    } else {
      const min = parseInt(raw.fields.getTextInputValue('minTokensPerPlay'), 10);
      const max = parseInt(raw.fields.getTextInputValue('maxTokensPerPlay'), 10);
      const straight = parseFloat(raw.fields.getTextInputValue('straightMultiplier'));
      const base = parseFloat(raw.fields.getTextInputValue('baseMultiplier'));
      const tripleLow = parseFloat(raw.fields.getTextInputValue('tripleLowBonus'));
      const tripleHigh = parseFloat(raw.fields.getTextInputValue('tripleHighBonus'));

      if (isNaN(min) || isNaN(max) || isNaN(straight) || isNaN(base) || isNaN(tripleLow) || isNaN(tripleHigh)) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.gameDice2Title)
          .setDescription(ZhTwStrings.gameValidationPositive)
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      const result = await this.facade.updateDiceGame2Config(guildId, {
        minTokensPerPlay: min,
        maxTokensPerPlay: max,
        straightMultiplier: straight,
        baseMultiplier: base,
        tripleLowBonus: tripleLow,
        tripleHighBonus: tripleHigh,
      });

      if (result.isOk()) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.gameDice2Title)
          .setDescription(ZhTwStrings.gameSaveSuccess)
          .setColor(Colors.SUCCESS);
        await interaction.editEmbed(embed);
      } else {
        await this.errorHandler.handle(result.getError(), interaction);
      }
    }
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
          .setColor(Colors.WARNING);
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
          .setColor(Colors.WARNING);
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
      .setColor(Colors.WARNING);
    await interaction.editEmbed(embed);
  }
}
