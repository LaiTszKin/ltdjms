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
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { GameConfigManagementFacade } from '../../../facades/GameConfigManagementFacade.js';
import { AdminPanelViewFactory } from '../views/AdminPanelViewFactory.js';
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
    private readonly viewFactory: AdminPanelViewFactory,
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

      await interaction.showModal(modal);
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
        new ActionRowBuilder<TextInputBuilder>().addComponents(
          new TextInputBuilder()
            .setCustomId('tripleHighBonus')
            .setLabel(ZhTwStrings.gameModalTripleHigh)
            .setStyle(TextInputStyle.Short)
            .setValue(String(cfg.tripleHighBonus))
            .setMinLength(1).setMaxLength(10).setRequired(true),
        ),
      );

      await interaction.showModal(modal);
    }
  }

  private async saveDiceGameConfig(
    interaction: DiscordInteraction,
    guildId: string,
    gameNumber: '1' | '2',
  ): Promise<void> {
    if (gameNumber === '1') {
      const min = parseInt(interaction.getTextInputValue('minTokensPerPlay'), 10);
      const max = parseInt(interaction.getTextInputValue('maxTokensPerPlay'), 10);
      const reward = parseInt(interaction.getTextInputValue('rewardPerDiceValue'), 10);

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
      const min = parseInt(interaction.getTextInputValue('minTokensPerPlay'), 10);
      const max = parseInt(interaction.getTextInputValue('maxTokensPerPlay'), 10);
      const straight = parseFloat(interaction.getTextInputValue('straightMultiplier'));
      const base = parseFloat(interaction.getTextInputValue('baseMultiplier'));
      const tripleLow = parseFloat(interaction.getTextInputValue('tripleLowBonus'));
      const tripleHigh = parseFloat(interaction.getTextInputValue('tripleHighBonus'));

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

  private async showGameOverview(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const dice1Result = await this.facade.getDiceGame1Config(guildId);
    const dice2Result = await this.facade.getDiceGame2Config(guildId);

    const dice1Config = dice1Result.isOk() ? dice1Result.getValue() : null;
    const dice2Config = dice2Result.isOk() ? dice2Result.getValue() : null;

    const embedData = this.viewFactory.buildGameOverviewEmbed(dice1Config, dice2Config);
    const embed = new EmbedBuilder()
      .setTitle(embedData.title)
      .setDescription(embedData.description)
      .setColor(embedData.color);

    const editBtn1 = new ButtonBuilder()
      .setCustomId('admin_game_edit_1')
      .setLabel('編輯骰子遊戲 1')
      .setStyle(ButtonStyle.Primary);

    const editBtn2 = new ButtonBuilder()
      .setCustomId('admin_game_edit_2')
      .setLabel('編輯骰子遊戲 2')
      .setStyle(ButtonStyle.Primary);

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(editBtn1, editBtn2);
    await interaction.editWithComponents(embed, [row]);
  }
}
