import {
  type DiscordInteraction,
  type DiscordContext,
  type DomainEventPublisher,
} from '@ltdjms/shared';
import {
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  OperationType,
} from '@ltdjms/shop';
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
import {
  type ShopService,
  type RedemptionCodeRepository,
  type RedemptionCodeGenerator,
  type ProductRepository,
  createRedemptionCode,
  type RedemptionCode,
} from '@ltdjms/shop';
import { AdminProductPanelViewFactory } from './AdminProductPanelViewFactory.js';
import { AdminProductPanelModalFactory } from './AdminProductPanelModalFactory.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Product-specific handler for the admin panel.
 * Manages the full product CRUD lifecycle with session state tracking.
 */
export class AdminProductPanelHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_product';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly shopService: ShopService,
    private readonly redemptionCodeRepo: RedemptionCodeRepository,
    private readonly codeGenerator: RedemptionCodeGenerator,
    private readonly productRepository: ProductRepository,
    private readonly eventPublisher: DomainEventPublisher,
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

    if (fullCustomId.startsWith('admin_product_generate_codes_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_generate_codes_', ''), 10);
      if (!isNaN(productId)) {
        await this.handleGenerateCodes(interaction, guildId, productId);
      }
      return;
    }

    if (fullCustomId === 'admin_product_create_save') {
      await this.handleCreateProduct(interaction, guildId);
      return;
    }

    if (fullCustomId.startsWith('admin_product_edit_save_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_edit_save_', ''), 10);
      if (!isNaN(productId)) {
        await this.handleEditProduct(interaction, guildId, productId);
      }
      return;
    }

    if (fullCustomId.startsWith('admin_product_codes_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_codes_', ''), 10);
      if (!isNaN(productId)) {
        await this.showGenerateCodesModal(interaction, productId);
      }
      return;
    }

    if (fullCustomId.startsWith('admin_product_detail_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_detail_', ''), 10);
      if (!isNaN(productId)) {
        await this.showProductDetail(interaction, guildId, productId);
      }
      return;
    }

    if (fullCustomId === 'admin_product_create') {
      await this.showCreateProductModal(interaction);
      return;
    }

    if (fullCustomId.startsWith('admin_product_fiat_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_fiat_', ''), 10);
      if (!isNaN(productId)) {
        await this.showSetFiatPriceModal(interaction, productId);
      }
      return;
    }

    if (fullCustomId.startsWith('admin_product_fiat_save_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_fiat_save_', ''), 10);
      if (!isNaN(productId)) {
        await this.handleSetFiatPrice(interaction, guildId, productId);
      }
      return;
    }

    if (fullCustomId.startsWith('admin_product_edit_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_edit_', ''), 10);
      if (!isNaN(productId)) {
        await this.showEditProductModal(interaction, guildId, productId);
      }
      return;
    }

    // Handle delete confirmation dialog
    if (fullCustomId.startsWith('admin_product_delete_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_delete_', ''), 10);
      if (!isNaN(productId)) {
        await this.showDeleteConfirmation(interaction, guildId, productId);
      }
      return;
    }

    // Handle confirmed delete execution
    if (fullCustomId.startsWith('admin_product_confirm_delete_')) {
      const productId = parseInt(fullCustomId.replace('admin_product_confirm_delete_', ''), 10);
      if (!isNaN(productId)) {
        await this.handleDeleteProduct(interaction, guildId, productId);
      }
      return;
    }

    if (fullCustomId === 'admin_product_prev') {
      const page = Math.max(1, parseInt(this.sessionManager.getContext(guildId, userId, 'productPage') ?? '1', 10) - 1);
      this.sessionManager.setContext(guildId, userId, 'productPage', String(page));
      await this.showProductList(interaction, guildId, page);
      return;
    }
    if (fullCustomId === 'admin_product_next') {
      const currentPage = parseInt(this.sessionManager.getContext(guildId, userId, 'productPage') ?? '1', 10);
      this.sessionManager.setContext(guildId, userId, 'productPage', String(currentPage + 1));
      await this.showProductList(interaction, guildId, currentPage + 1);
      return;
    }

    if (fullCustomId === 'admin_product_back') {
      this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.PRODUCT_LIST);
      const page = parseInt(this.sessionManager.getContext(guildId, userId, 'productPage') ?? '1', 10);
      await this.showProductList(interaction, guildId, page);
      return;
    }

    const page = parseInt(this.sessionManager.getContext(guildId, userId, 'productPage') ?? '1', 10);
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

    await interaction.showModal(modal);
  }

  private async showEditProductModal(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    const product = await this.productRepository.findById(productId);

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

    await interaction.showModal(modal);
  }

  private async handleCreateProduct(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const name = interaction.getTextInputValue(ZhTwStrings.productModalName).trim();
    const description = interaction.getTextInputValue(ZhTwStrings.productModalDesc).trim() || null;
    const currencyPriceStr = interaction.getTextInputValue(ZhTwStrings.productModalPrice).trim();
    const fiatPriceStr = interaction.getTextInputValue(ZhTwStrings.productModalFiatPrice).trim();
    const imageUrl = interaction.getTextInputValue(ZhTwStrings.productModalImageUrl).trim() || null;

    if (!name) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('產品名稱為必填欄位')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const currencyPrice = currencyPriceStr ? parseInt(currencyPriceStr, 10) : null;
    const fiatPriceTwd = fiatPriceStr ? parseInt(fiatPriceStr, 10) : null;

    if (currencyPriceStr && (currencyPrice === null || isNaN(currencyPrice) || currencyPrice < 0)) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('請輸入有效的貨幣價格')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    if (fiatPriceStr && (fiatPriceTwd === null || isNaN(fiatPriceTwd) || fiatPriceTwd < 0)) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('請輸入有效的法幣價格')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    try {
      const product = await this.productRepository.create({
        guildId: Number(guildId),
        name,
        description,
        rewardType: null,
        rewardAmount: null,
        currencyPrice: currencyPrice ?? null,
        fiatPriceTwd: fiatPriceTwd ?? null,
        autoCreateEscortOrder: false,
        escortOptionCode: null,
      });

      if (imageUrl) {
        this.sessionManager.setContext(guildId, String(product.id!), 'productImageUrl', imageUrl);
      }

      this.eventPublisher.publish({
        eventType: 'product_changed',
        guildId,
        productId: product.id!,
        operationType: OperationType.CREATED,
      } as ProductChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription(ZhTwStrings.productCreated.replace('{name}', product.name))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async handleEditProduct(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    const name = interaction.getTextInputValue(ZhTwStrings.productModalName).trim();
    const description = interaction.getTextInputValue(ZhTwStrings.productModalDesc).trim() || null;
    const currencyPriceStr = interaction.getTextInputValue(ZhTwStrings.productModalPrice).trim();
    const fiatPriceStr = interaction.getTextInputValue(ZhTwStrings.productModalFiatPrice).trim();
    const imageUrl = interaction.getTextInputValue(ZhTwStrings.productModalImageUrl).trim() || null;

    if (!name) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('產品名稱為必填欄位')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const currencyPrice = currencyPriceStr ? parseInt(currencyPriceStr, 10) : null;
    const fiatPriceTwd = fiatPriceStr ? parseInt(fiatPriceStr, 10) : null;

    try {
      const product = await this.productRepository.update(productId, {
        name,
        description,
        currencyPrice,
        fiatPriceTwd,
      });

      if (!product) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.productListTitle)
          .setDescription('找不到該產品')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      if (imageUrl) {
        this.sessionManager.setContext(guildId, String(productId), 'productImageUrl', imageUrl);
      }

      this.eventPublisher.publish({
        eventType: 'product_changed',
        guildId,
        productId,
        operationType: OperationType.UPDATED,
      } as ProductChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription(ZhTwStrings.productUpdated.replace('{name}', product.name))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async showDeleteConfirmation(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    const product = await this.productRepository.findById(productId);
    const name = product?.name ?? String(productId);

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.productListTitle)
      .setDescription(
        ZhTwStrings.productConfirmDelete.replace('{name}', name),
      )
      .setColor(Colors.WARNING);

    const confirmBtn = new ButtonBuilder()
      .setCustomId('admin_product_confirm_delete_' + productId)
      .setLabel('確認刪除')
      .setStyle(ButtonStyle.Danger);

    const cancelBtn = new ButtonBuilder()
      .setCustomId('admin_product_back')
      .setLabel('取消')
      .setStyle(ButtonStyle.Secondary);

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(confirmBtn, cancelBtn);

    await interaction.editWithComponents(embed, [row]);
  }

  private async handleDeleteProduct(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    try {
      const product = await this.productRepository.findById(productId);
      const name = product?.name ?? String(productId);

      const deleted = await this.productRepository.delete(productId);

      if (!deleted) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.productListTitle)
          .setDescription('找不到該產品')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      this.eventPublisher.publish({
        eventType: 'product_changed',
        guildId,
        productId,
        operationType: OperationType.DELETED,
      } as ProductChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription(ZhTwStrings.productDeleted.replace('{name}', name))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
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

    await interaction.showModal(modal);
  }

  private async handleGenerateCodes(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    const countStr = interaction.getTextInputValue(ZhTwStrings.generateCodesCountLabel);
    const count = parseInt(countStr, 10);

    if (isNaN(count) || count <= 0 || count > 100) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.generateCodesModalTitle)
        .setDescription('請輸入 1-100 之間的數量')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const note = interaction.getTextInputValue(ZhTwStrings.generateCodesNoteLabel).trim();
    const daysStr = interaction.getTextInputValue(ZhTwStrings.generateCodesDaysLabel).trim();

    let expiresAt: Date | null = null;
    if (daysStr) {
      const days = parseInt(daysStr, 10);
      if (!isNaN(days) && days > 0) {
        expiresAt = new Date();
        expiresAt.setDate(expiresAt.getDate() + days);
      }
    }

    try {
      const codeStrings: string[] = [];
      for (let i = 0; i < count; i++) {
        codeStrings.push(this.codeGenerator.generate());
      }

      const redemptionCodes: RedemptionCode[] = codeStrings.map((codeStr) =>
        createRedemptionCode(codeStr, productId, Number(guildId), expiresAt),
      );
      const savedCodes = await this.redemptionCodeRepo.saveAll(redemptionCodes);

      this.eventPublisher.publish({
        eventType: 'redemption_codes_generated',
        guildId,
        productId,
        count,
      } as RedemptionCodesGeneratedEvent);

      this.sessionManager.setViewState(guildId, interaction.getUserId(), AdminPanelViewState.PRODUCT_CODE_LIST);

      const product = await this.productRepository.findById(productId);
      const productName = product?.name ?? String(productId);

      const displayCodes = savedCodes.map((c: RedemptionCode) => ({ code: c.code, redeemed: c.redeemedBy !== null }));
      const embedData = this.viewFactory.buildProductCodeListEmbed(displayCodes, productName, 1);
      const embed = new EmbedBuilder()
        .setTitle(embedData.title)
        .setDescription(embedData.description)
        .setColor(embedData.color);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async showSetFiatPriceModal(
    interaction: DiscordInteraction,
    productId: number,
  ): Promise<void> {
    const modal = new ModalBuilder()
      .setCustomId('admin_product_fiat_save_' + productId)
      .setTitle(ZhTwStrings.productFiatPriceBtn);

    const input = new TextInputBuilder()
      .setCustomId(ZhTwStrings.productModalFiatPrice)
      .setLabel(ZhTwStrings.productModalFiatPrice)
      .setStyle(TextInputStyle.Short)
      .setMinLength(1)
      .setMaxLength(20)
      .setRequired(true)
      .setPlaceholder(ZhTwStrings.productModalFiatPricePlaceholder);

    modal.addComponents(new ActionRowBuilder<TextInputBuilder>().addComponents(input));

    await interaction.showModal(modal);
  }

  private async handleSetFiatPrice(
    interaction: DiscordInteraction,
    guildId: string,
    productId: number,
  ): Promise<void> {
    const fiatPriceStr = interaction.getTextInputValue(ZhTwStrings.productModalFiatPrice).trim();
    const fiatPriceTwd = fiatPriceStr ? parseInt(fiatPriceStr, 10) : null;

    if (fiatPriceTwd === null || isNaN(fiatPriceTwd) || fiatPriceTwd < 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription('請輸入有效的法幣價格')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    try {
      const product = await this.productRepository.update(productId, { fiatPriceTwd });
      if (!product) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.productListTitle)
          .setDescription('找不到該產品')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      this.eventPublisher.publish({
        eventType: 'product_changed',
        guildId,
        productId,
        operationType: OperationType.UPDATED,
      } as ProductChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.productListTitle)
        .setDescription(ZhTwStrings.productFiatPriceSet.replace('{price}', String(fiatPriceTwd)))
        .setColor(Colors.SUCCESS);
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
      this.sessionManager.setViewState(guildId, interaction.getUserId(), AdminPanelViewState.PRODUCT_DETAIL);

      const product = await this.productRepository.findById(productId);

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

      const editBtn = new ButtonBuilder()
        .setCustomId('admin_product_edit_' + productId)
        .setLabel(ZhTwStrings.productEditBtn)
        .setStyle(ButtonStyle.Primary);

      const fiatPriceBtn = new ButtonBuilder()
        .setCustomId('admin_product_fiat_' + productId)
        .setLabel(ZhTwStrings.productFiatPriceBtn)
        .setStyle(ButtonStyle.Primary);

      const deleteBtn = new ButtonBuilder()
        .setCustomId('admin_product_delete_' + productId)
        .setLabel(ZhTwStrings.productDeleteBtn)
        .setStyle(ButtonStyle.Danger);

      const backBtn = new ButtonBuilder()
        .setCustomId('admin_product_back')
        .setLabel(ZhTwStrings.productBackBtn)
        .setStyle(ButtonStyle.Secondary);

      const row1 = new ActionRowBuilder<ButtonBuilder>().addComponents(generateCodesBtn, editBtn, fiatPriceBtn);
      const row2 = new ActionRowBuilder<ButtonBuilder>().addComponents(deleteBtn, backBtn);

      await interaction.editWithComponents(embed, [row1, row2]);
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

      if (shopPage.products.length > 0) {
        const detailButtons = shopPage.products.map((p) =>
          new ButtonBuilder()
            .setCustomId('admin_product_detail_' + p.id)
            .setLabel(`📄 ${p.name}`)
            .setStyle(ButtonStyle.Secondary),
        );
        const chunkSize = 5;
        for (let i = 0; i < detailButtons.length; i += chunkSize) {
          const chunk = detailButtons.slice(i, i + chunkSize);
          if (chunk.length > 0) {
            rows.push(new ActionRowBuilder<ButtonBuilder>().addComponents(chunk));
          }
        }
      }

      await interaction.editWithComponents(embed, rows);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }
}
