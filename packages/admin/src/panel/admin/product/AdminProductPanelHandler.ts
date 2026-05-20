import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { type ShopService, type RedemptionCodeRepository, type RedemptionCodeGenerator } from '@ltdjms/shop';
import { AdminProductPanelViewFactory } from './AdminProductPanelViewFactory.js';
import { AdminProductPanelModalFactory } from './AdminProductPanelModalFactory.js';
import { Colors } from '../../../constants/colors.js';

/**
 * In-memory page tracker for product list per guild.
 */
const pageTracker = new Map<string, number>();

function getPage(guildId: string): number {
  return pageTracker.get(guildId) ?? 1;
}

function setPage(guildId: string, page: number): void {
  pageTracker.set(guildId, page);
}

/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 * Matches Java AdminProductPanelHandler.
 */
export class AdminProductPanelHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_product';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly shopService: ShopService,
    private readonly redemptionCodeRepo: RedemptionCodeRepository,
    private readonly codeGenerator: RedemptionCodeGenerator,
    private readonly viewFactory: AdminProductPanelViewFactory,
    private readonly modalFactory: AdminProductPanelModalFactory,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.PRODUCT_LIST);

    const fullCustomId = interaction.getCustomId();

    // Handle code generation modal submit
    if (fullCustomId.startsWith('admin_product_generate_codes_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_generate_codes_', ''), 10);
      if (!isNaN(productId)) {
        await this.handleGenerateCodes(interaction, productId);
      }
      return;
    }

    // Handle create product modal submit
    if (fullCustomId === 'admin_product_create_save') {
      await this.handleCreateProduct(interaction, guildId);
      return;
    }

    // Handle edit product modal submit
    if (fullCustomId.startsWith('admin_product_edit_save_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_edit_save_', ''), 10);
      if (!isNaN(productId)) {
        await this.handleEditProduct(interaction, guildId, productId);
      }
      return;
    }

    // Show generate codes modal
    if (fullCustomId.startsWith('admin_product_codes_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_codes_', ''), 10);
      if (!isNaN(productId)) {
        await this.showGenerateCodesModal(interaction, productId);
      }
      return;
    }

    // Show product detail
    if (fullCustomId.startsWith('admin_product_detail_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_detail_', ''), 10);
      if (!isNaN(productId)) {
        await this.showProductDetail(interaction, guildId, productId);
      }
      return;
    }

    // Show create modal
    if (fullCustomId === 'admin_product_create') {
      await this.showCreateProductModal(interaction);
      return;
    }

    // Show edit modal
    if (fullCustomId.startsWith('admin_product_edit_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_edit_', ''), 10);
      if (!isNaN(productId)) {
        await this.showEditProductModal(interaction, guildId, productId);
      }
      return;
    }

    // Handle pagination
    if (fullCustomId === 'admin_product_prev') {
      const page = Math.max(1, getPage(guildId) - 1);
      setPage(guildId, page);
      await this.showProductList(interaction, guildId, page);
      return;
    }
    if (fullCustomId === 'admin_product_next') {
      const currentPage = getPage(guildId);
      setPage(guildId, currentPage + 1);
      await this.showProductList(interaction, guildId, currentPage + 1);
      return;
    }

    if (fullCustomId === 'admin_product_back') {
      // Back to main state
      this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.MAIN);
      return;
    }

    // Default: show product list
    const page = getPage(guildId);
    await this.showProductList(interaction, guildId, page);
  }

  private async showCreateProductModal(interaction: DiscordInteraction): Promise<void> {
    const modalData = this.modalFactory.buildCreateProductModal();

    const modal = new ModalBuilder()
      .setCustomId('admin_product_create_save')
      .setTitle(modalData.title);

    for (const field of modalData.fields) {
      const input = new TextInputBuilder()
        .setCustomId(field.label)
        .setLabel(field.label)
        .setStyle(TextInputStyle.Short)
        .setMinLength(field.minLength)
        .setMaxLength(field.maxLength)
        .setRequired(field.required);
      if ('placeholder' in field && field.placeholder) {
        input.setPlaceholder(field.placeholder);
      }
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
    await raw.showModal(modal);
  }

  private async showEditProductModal(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    const shopPage = await this.shopService.getShopPage(Number(guildId), 0);
    const product = shopPage.products.find((p) => p.id === productId);

    if (!product) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('找不到該產品')
        .setColor(Colors.PRODUCT_DEFAULT);
      await interaction.editEmbed(embed);
      return;
    }

    const modalData = this.modalFactory.buildEditProductModal(product);

    const modal = new ModalBuilder()
      .setCustomId('admin_product_edit_save_' + productId)
      .setTitle(modalData.title);

    for (const field of modalData.fields) {
      const input = new TextInputBuilder()
        .setCustomId(field.label)
        .setLabel(field.label)
        .setStyle(TextInputStyle.Short)
        .setMinLength(field.minLength)
        .setMaxLength(field.maxLength)
        .setRequired(field.required);
      if ('value' in field) {
        input.setValue(field.value);
      }
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
    await raw.showModal(modal);
  }

  private async handleCreateProduct(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    // NOTE: Full product creation via service layer requires
    // ProductRepository.create() which is not yet implemented.
    // For now, acknowledge the limitation.
    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.productListTitle)
      .setDescription('產品建立功能需要擴充 ProductRepository 後方可使用')
      .setColor(Colors.WARNING);
    await interaction.editEmbed(embed);
  }

  private async handleEditProduct(
    interaction: DiscordInteraction,
    guildId: string,
    _productId: number,
  ): Promise<void> {
    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.productListTitle)
      .setDescription('產品編輯功能需要擴充 ProductRepository 後方可使用')
      .setColor(Colors.WARNING);
    await interaction.editEmbed(embed);
  }

  private async showGenerateCodesModal(
    interaction: DiscordInteraction,
    productId: number,
  ): Promise<void> {
    const modalData = this.modalFactory.buildGenerateCodesModal();

    const modal = new ModalBuilder()
      .setCustomId('admin_product_generate_codes_' + productId)
      .setTitle(modalData.title);

    for (const field of modalData.fields) {
      const input = new TextInputBuilder()
        .setCustomId(field.label)
        .setLabel(field.label)
        .setStyle(TextInputStyle.Short)
        .setMinLength(field.minLength)
        .setMaxLength(field.maxLength)
        .setRequired(field.required);
      if ('placeholder' in field && field.placeholder) {
        input.setPlaceholder(field.placeholder);
      }
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
    await raw.showModal(modal);
  }

  private async handleGenerateCodes(
    interaction: DiscordInteraction,
    productId: number,
  ): Promise<void> {
    const raw = interaction.getHook() as {
      fields: { getTextInputValue: (id: string) => string };
    };

    const countStr = raw.fields.getTextInputValue(ZhTwStrings.generateCodesCountLabel);
    const count = parseInt(countStr, 10);

    if (isNaN(count) || count <= 0 || count > 100) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.generateCodesModalTitle)
        .setDescription('請輸入 1-100 之間的數量')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    try {
      const codes: Array<{ code: string; redeemed: boolean }> = [];
      const existingCodes: string[] = [];

      for (let i = 0; i < count; i++) {
        const code = this.codeGenerator.generate();
        existingCodes.push(code);
        codes.push({ code, redeemed: false });
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productCodesTitle.replace('{name}', String(productId)))
        .setDescription(
          `已生成 ${count} 個兌換碼（注意：實際上需透過 RedemptionService.generateCodes 寫入資料庫）\n\n` +
          codes.map((c) => `\`${c.code}\``).join('\n'),
        )
        .setColor(Colors.PRODUCT_CODES);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async showProductDetail(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    try {
      const shopPage = await this.shopService.getShopPage(Number(guildId), 0);
      const product = shopPage.products.find((p) => p.id === productId);

      if (!product) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.productListTitle)
          .setDescription('找不到該產品')
          .setColor(Colors.PRODUCT_DEFAULT);
        await interaction.editEmbed(embed);
        return;
      }

      const codeStats = await this.redemptionCodeRepo.getStatsByProductId(productId);

      const embedData = this.viewFactory.buildProductDetailEmbed(product, codeStats);
      const embed = new EmbedBuilder()
        .setTitle(embedData.title)
        .setDescription(embedData.description)
        .setColor(embedData.color);

      for (const field of embedData.fields) {
        embed.addFields({ name: field.name, value: field.value, inline: field.inline });
      }

      const generateCodesBtn = new ButtonBuilder()
        .setCustomId('admin_product_codes_' + productId)
        .setLabel(ZhTwStrings.productGenerateCodesBtn)
        .setStyle(ButtonStyle.Primary);

      const backBtn = new ButtonBuilder()
        .setCustomId('admin_product_back')
        .setLabel(ZhTwStrings.productBackBtn)
        .setStyle(ButtonStyle.Secondary);

      const row = new ActionRowBuilder<ButtonBuilder>().addComponents(generateCodesBtn, backBtn);

      const raw = interaction.getHook() as {
        editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
      };
      await raw.editReply({ embeds: [embed], components: [row] });
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async showProductList(
    interaction: DiscordInteraction,
    guildId: string,
    page: number,
  ): Promise<void> {
    try {
      const shopPage = await this.shopService.getShopPage(Number(guildId), page);

      const embedData = this.viewFactory.buildProductListEmbed(
        shopPage.products,
        shopPage.currentPage,
        shopPage.totalPages,
      );

      const embed = new EmbedBuilder()
        .setTitle(embedData.title)
        .setDescription(embedData.description)
        .setColor(embedData.color);

      for (const field of embedData.fields) {
        embed.addFields({ name: field.name, value: field.value, inline: field.inline });
      }

      const rows: ActionRowBuilder<ButtonBuilder>[] = [];
      const buttons: ButtonBuilder[] = [];

      buttons.push(
        new ButtonBuilder()
          .setCustomId('admin_product_prev')
          .setLabel(ZhTwStrings.historyPrevBtn)
          .setStyle(ButtonStyle.Secondary)
          .setDisabled(page <= 1),
      );

      buttons.push(
        new ButtonBuilder()
          .setCustomId('admin_product_next')
          .setLabel(ZhTwStrings.historyNextBtn)
          .setStyle(ButtonStyle.Secondary)
          .setDisabled(page >= shopPage.totalPages),
      );

      buttons.push(
        new ButtonBuilder()
          .setCustomId('admin_product_create')
          .setLabel(ZhTwStrings.productCreateBtn)
          .setStyle(ButtonStyle.Success),
      );

      buttons.push(
        new ButtonBuilder()
          .setCustomId('admin_product_back')
          .setLabel(ZhTwStrings.productBackBtn)
          .setStyle(ButtonStyle.Secondary),
      );

      rows.push(new ActionRowBuilder<ButtonBuilder>().addComponents(buttons));

      // Add product detail buttons in a second row if there are products
      if (shopPage.products.length > 0) {
        const detailButtons = shopPage.products.map((p) =>
          new ButtonBuilder()
            .setCustomId('admin_product_detail_' + p.id)
            .setLabel(`📄 ${p.name}`)
            .setStyle(ButtonStyle.Secondary),
        );
        // Discord allows max 5 buttons per row
        const chunkSize = 5;
        for (let i = 0; i < detailButtons.length; i += chunkSize) {
          const chunk = detailButtons.slice(i, i + chunkSize);
          if (chunk.length > 0) {
            rows.push(new ActionRowBuilder<ButtonBuilder>().addComponents(chunk));
          }
        }
      }

      const raw = interaction.getHook() as {
        editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
      };
      await raw.editReply({ embeds: [embed], components: rows });
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }
}
