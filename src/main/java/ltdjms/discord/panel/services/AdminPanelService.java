package ltdjms.discord.panel.services;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.aichat.domain.AllowedCategory;
import ltdjms.discord.aichat.domain.AllowedChannel;
import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.dispatch.services.DispatchAfterSalesStaffService;
import ltdjms.discord.dispatch.services.EscortOptionPricingService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.product.domain.EscortOptionCatalog;
import ltdjms.discord.product.domain.EscortOptionCatalogRepository;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.MembershipAdminDetail;
import ltdjms.discord.shared.Unit;

/**
 * Service for admin panel operations. Provides methods for managing member balances, game tokens,
 * and game settings through facade services.
 *
 * <p>This service has been refactored to use facade services, reducing dependencies from 10 to 4.
 */
public class AdminPanelService {

  private static final Logger LOG = LoggerFactory.getLogger(AdminPanelService.class);

  private final CurrencyManagementFacade currencyFacade;
  private final GameTokenManagementFacade gameTokenFacade;
  private final GameConfigManagementFacade gameConfigFacade;
  private final AIConfigManagementFacade aiConfigFacade;
  private final DispatchAfterSalesStaffService dispatchAfterSalesStaffService;
  private final EscortOptionPricingService escortOptionPricingService;
  private final EscortOptionCatalogRepository escortOptionCatalogRepository;
  private final ProductRepository productRepository;
  private final MembershipManagementFacade membershipManagementFacade;

  public AdminPanelService(
      CurrencyManagementFacade currencyFacade,
      GameTokenManagementFacade gameTokenFacade,
      GameConfigManagementFacade gameConfigFacade,
      AIConfigManagementFacade aiConfigFacade,
      DispatchAfterSalesStaffService dispatchAfterSalesStaffService) {
    this(
        currencyFacade,
        gameTokenFacade,
        gameConfigFacade,
        aiConfigFacade,
        dispatchAfterSalesStaffService,
        null,
        null,
        null,
        null);
  }

  public AdminPanelService(
      CurrencyManagementFacade currencyFacade,
      GameTokenManagementFacade gameTokenFacade,
      GameConfigManagementFacade gameConfigFacade,
      AIConfigManagementFacade aiConfigFacade,
      DispatchAfterSalesStaffService dispatchAfterSalesStaffService,
      EscortOptionPricingService escortOptionPricingService) {
    this(
        currencyFacade,
        gameTokenFacade,
        gameConfigFacade,
        aiConfigFacade,
        dispatchAfterSalesStaffService,
        escortOptionPricingService,
        null,
        null,
        null);
  }

  public AdminPanelService(
      CurrencyManagementFacade currencyFacade,
      GameTokenManagementFacade gameTokenFacade,
      GameConfigManagementFacade gameConfigFacade,
      AIConfigManagementFacade aiConfigFacade,
      DispatchAfterSalesStaffService dispatchAfterSalesStaffService,
      EscortOptionPricingService escortOptionPricingService,
      EscortOptionCatalogRepository escortOptionCatalogRepository,
      ProductRepository productRepository) {
    this(
        currencyFacade,
        gameTokenFacade,
        gameConfigFacade,
        aiConfigFacade,
        dispatchAfterSalesStaffService,
        escortOptionPricingService,
        escortOptionCatalogRepository,
        productRepository,
        null);
  }

  public AdminPanelService(
      CurrencyManagementFacade currencyFacade,
      GameTokenManagementFacade gameTokenFacade,
      GameConfigManagementFacade gameConfigFacade,
      AIConfigManagementFacade aiConfigFacade,
      DispatchAfterSalesStaffService dispatchAfterSalesStaffService,
      EscortOptionPricingService escortOptionPricingService,
      EscortOptionCatalogRepository escortOptionCatalogRepository,
      ProductRepository productRepository,
      MembershipManagementFacade membershipManagementFacade) {
    this.currencyFacade = currencyFacade;
    this.gameTokenFacade = gameTokenFacade;
    this.gameConfigFacade = gameConfigFacade;
    this.aiConfigFacade = aiConfigFacade;
    this.dispatchAfterSalesStaffService = dispatchAfterSalesStaffService;
    this.escortOptionPricingService = escortOptionPricingService;
    this.escortOptionCatalogRepository = escortOptionCatalogRepository;
    this.productRepository = productRepository;
    this.membershipManagementFacade = membershipManagementFacade;
  }

  // ========== Currency Management ==========

  /**
   * Gets the currency configuration for a guild.
   *
   * @param guildId the guild ID
   * @return the currency configuration with name and icon
   */
  public Result<GuildCurrencyConfig, DomainError> getCurrencyConfig(long guildId) {
    return currencyFacade.getCurrencyConfig(guildId);
  }

  /** Gets a member's current currency balance. */
  public Result<Long, DomainError> getMemberBalance(long guildId, long userId) {
    return currencyFacade.getMemberBalance(guildId, userId);
  }

  /** Adjusts a member's currency balance. */
  public Result<CurrencyManagementFacade.BalanceAdjustmentResult, DomainError> adjustBalance(
      long guildId, long userId, String mode, long amount) {
    LOG.debug(
        "Admin panel adjusting balance: guildId={}, userId={}, mode={}, amount={}",
        guildId,
        userId,
        mode,
        amount);
    return currencyFacade.adjustBalance(guildId, userId, mode, amount);
  }

  // ========== Game Token Management ==========

  /** Gets a member's current game token balance. */
  public long getMemberTokens(long guildId, long userId) {
    return gameTokenFacade.getMemberTokens(guildId, userId);
  }

  /**
   * Adjusts a member's game token balance using mode-based adjustment.
   *
   * @param guildId the guild ID
   * @param userId the user ID
   * @param mode the adjustment mode: "add", "deduct", or "adjust"
   * @param amount the amount to add/deduct or target balance for "adjust" mode
   * @return the result of the adjustment
   */
  public Result<GameTokenManagementFacade.TokenAdjustmentResult, DomainError> adjustTokens(
      long guildId, long userId, String mode, long amount) {
    LOG.debug(
        "Admin panel adjusting tokens: guildId={}, userId={}, mode={}, amount={}",
        guildId,
        userId,
        mode,
        amount);
    return gameTokenFacade.adjustTokens(guildId, userId, mode, amount);
  }

  // ========== Game Configuration Management ==========

  /** Gets the full configuration for dice-game-1. */
  public DiceGame1Config getDiceGame1Config(long guildId) {
    return gameConfigFacade.getDiceGame1Config(guildId);
  }

  /** Gets the full configuration for dice-game-2. */
  public DiceGame2Config getDiceGame2Config(long guildId) {
    return gameConfigFacade.getDiceGame2Config(guildId);
  }

  /** Updates dice-game-1 configuration. */
  public Result<DiceGame1Config, DomainError> updateDiceGame1Config(
      long guildId, Long minTokens, Long maxTokens, Long rewardPerDice) {
    LOG.debug(
        "Admin panel updating dice-game-1 config: guildId={}, min={}, max={}, reward={}",
        guildId,
        minTokens,
        maxTokens,
        rewardPerDice);
    return gameConfigFacade.updateDiceGame1Config(guildId, minTokens, maxTokens, rewardPerDice);
  }

  /** Updates dice-game-2 configuration. */
  public Result<DiceGame2Config, DomainError> updateDiceGame2Config(
      long guildId,
      Long minTokens,
      Long maxTokens,
      Long straightMultiplier,
      Long baseMultiplier,
      Long tripleLowBonus,
      Long tripleHighBonus) {
    LOG.debug(
        "Admin panel updating dice-game-2 config: guildId={}, min={}, max={}, "
            + "straight={}, base={}, tripleLow={}, tripleHigh={}",
        guildId,
        minTokens,
        maxTokens,
        straightMultiplier,
        baseMultiplier,
        tripleLowBonus,
        tripleHighBonus);
    return gameConfigFacade.updateDiceGame2Config(
        guildId,
        minTokens,
        maxTokens,
        straightMultiplier,
        baseMultiplier,
        tripleLowBonus,
        tripleHighBonus);
  }

  // ========== AI 頻道設定管理 ==========

  /**
   * 獲取伺服器的所有允許頻道。
   *
   * @param guildId 伺服器 ID
   * @return 允許頻道集合（空集合表示尚未設定任何允許頻道）
   */
  public Result<Set<AllowedChannel>, DomainError> getAllowedChannels(long guildId) {
    LOG.debug("Admin panel getting allowed channels for guildId={}", guildId);
    return aiConfigFacade.getAllowedChannels(guildId);
  }

  /**
   * 獲取伺服器的所有允許類別。
   *
   * @param guildId 伺服器 ID
   * @return 允許類別集合（空集合表示未設定類別限制）
   */
  public Result<Set<AllowedCategory>, DomainError> getAllowedCategories(long guildId) {
    LOG.debug("Admin panel getting allowed categories for guildId={}", guildId);
    return aiConfigFacade.getAllowedCategories(guildId);
  }

  /**
   * 新增允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param channelName 頻道名稱（用於顯示）
   * @return 成功返回頻道，失敗返回錯誤
   */
  public Result<AllowedChannel, DomainError> addAllowedChannel(
      long guildId, long channelId, String channelName) {
    LOG.info(
        "Admin panel adding allowed channel: guildId={}, channelId={}, channelName={}",
        guildId,
        channelId,
        channelName);
    return aiConfigFacade.addAllowedChannel(guildId, channelId, channelName);
  }

  /**
   * 新增允許類別。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 類別 ID
   * @param categoryName 類別名稱
   * @return 成功返回類別，失敗返回錯誤
   */
  public Result<AllowedCategory, DomainError> addAllowedCategory(
      long guildId, long categoryId, String categoryName) {
    LOG.info(
        "Admin panel adding allowed category: guildId={}, categoryId={}, categoryName={}",
        guildId,
        categoryId,
        categoryName);
    return aiConfigFacade.addAllowedCategory(guildId, categoryId, categoryName);
  }

  /**
   * 移除允許頻道。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAllowedChannel(long guildId, long channelId) {
    LOG.info("Admin panel removing allowed channel: guildId={}, channelId={}", guildId, channelId);
    return aiConfigFacade.removeAllowedChannel(guildId, channelId);
  }

  /**
   * 移除允許類別。
   *
   * @param guildId 伺服器 ID
   * @param categoryId 類別 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAllowedCategory(long guildId, long categoryId) {
    LOG.info(
        "Admin panel removing allowed category: guildId={}, categoryId={}", guildId, categoryId);
    return aiConfigFacade.removeAllowedCategory(guildId, categoryId);
  }

  // ========== AI Agent 頻道配置管理 ==========

  /**
   * 獲取伺服器中已啟用 AI Agent 模式的頻道列表。
   *
   * @param guildId 伺服器 ID
   * @return 已啟用的頻道 ID 列表
   */
  public Result<java.util.List<Long>, DomainError> getEnabledAgentChannels(long guildId) {
    LOG.debug("Admin panel getting enabled agent channels for guildId={}", guildId);
    return aiConfigFacade.getEnabledAgentChannels(guildId);
  }

  /**
   * 檢查頻道是否啟用 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 是否啟用
   */
  public boolean isAgentEnabled(long guildId, long channelId) {
    return aiConfigFacade.isAgentEnabled(guildId, channelId);
  }

  /**
   * 啟用頻道的 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> enableAgentChannel(long guildId, long channelId) {
    LOG.info(
        "Admin panel enabling agent for channel: guildId={}, channelId={}", guildId, channelId);
    return aiConfigFacade.enableAgentChannel(guildId, channelId);
  }

  /**
   * 停用頻道的 AI Agent 模式。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> disableAgentChannel(long guildId, long channelId) {
    LOG.info(
        "Admin panel disabling agent for channel: guildId={}, channelId={}", guildId, channelId);
    return aiConfigFacade.disableAgentChannel(guildId, channelId);
  }

  /**
   * 移除頻道的 AI Agent 配置。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @return 成功返回 Unit，失敗返回錯誤
   */
  public Result<Unit, DomainError> removeAgentChannel(long guildId, long channelId) {
    LOG.info(
        "Admin panel removing agent channel config: guildId={}, channelId={}", guildId, channelId);
    return aiConfigFacade.removeAgentChannel(guildId, channelId);
  }

  // ========== Dispatch 售後人員設定 ==========

  public Result<java.util.Set<Long>, DomainError> getDispatchAfterSalesStaff(long guildId) {
    LOG.debug("Admin panel getting dispatch after-sales staff: guildId={}", guildId);
    return dispatchAfterSalesStaffService.getStaffUserIds(guildId);
  }

  public Result<Unit, DomainError> addDispatchAfterSalesStaff(long guildId, long userId) {
    LOG.info(
        "Admin panel adding dispatch after-sales staff: guildId={}, userId={}", guildId, userId);
    return dispatchAfterSalesStaffService.addStaff(guildId, userId);
  }

  public Result<Unit, DomainError> removeDispatchAfterSalesStaff(long guildId, long userId) {
    LOG.info(
        "Admin panel removing dispatch after-sales staff: guildId={}, userId={}", guildId, userId);
    return dispatchAfterSalesStaffService.removeStaff(guildId, userId);
  }

  // ========== Membership Management ==========

  public Result<MembershipAdminDetail, DomainError> getMembershipDetail(long userId) {
    if (membershipManagementFacade == null) {
      return Result.err(DomainError.unexpectedFailure("會員管理服務尚未初始化", null));
    }
    return membershipManagementFacade.getDetail(userId);
  }

  public Result<Unit, DomainError> adjustMembershipSpend(
      long guildId, long userId, long adminUserId, String mode, long amountM) {
    if (membershipManagementFacade == null) {
      return Result.err(DomainError.unexpectedFailure("會員管理服務尚未初始化", null));
    }
    LOG.debug(
        "Admin panel adjusting membership spend: guildId={}, userId={}, adminUserId={}, mode={}, amountM={}",
        guildId,
        userId,
        adminUserId,
        mode,
        amountM);
    return membershipManagementFacade.adjustPeriodSpend(
        userId, guildId, adminUserId, mode, amountM);
  }

  public Result<MembershipTier, DomainError> setMembershipTier(
      long userId, long adminUserId, MembershipTier newTier) {
    if (membershipManagementFacade == null) {
      return Result.err(DomainError.unexpectedFailure("會員管理服務尚未初始化", null));
    }
    LOG.debug(
        "Admin panel setting membership tier: userId={}, adminUserId={}, tier={}",
        userId,
        adminUserId,
        newTier);
    return membershipManagementFacade.setTier(userId, adminUserId, newTier);
  }

  // ========== Escort 定價管理 ==========

  public Result<java.util.List<EscortOptionPricingService.OptionPriceView>, DomainError>
      getEscortOptionPrices(long guildId) {
    if (escortOptionPricingService == null) {
      return Result.err(DomainError.unexpectedFailure("護航定價服務尚未初始化", null));
    }
    return escortOptionPricingService.listOptionPrices(guildId);
  }

  public Result<EscortOptionPricingService.OptionPriceView, DomainError> updateEscortOptionPrice(
      long guildId, long updatedByUserId, String optionCode, long priceTwd) {
    if (escortOptionPricingService == null) {
      return Result.err(DomainError.unexpectedFailure("護航定價服務尚未初始化", null));
    }
    return escortOptionPricingService.updateOptionPrice(
        guildId, updatedByUserId, optionCode, priceTwd);
  }

  public Result<Unit, DomainError> resetEscortOptionPrice(long guildId, String optionCode) {
    if (escortOptionPricingService == null) {
      return Result.err(DomainError.unexpectedFailure("護航定價服務尚未初始化", null));
    }
    return escortOptionPricingService.resetOptionPrice(guildId, optionCode);
  }

  // ========== 護航價目表管理（Catalog CRUD）==========

  /** Returns a paginated view of the escort catalog. */
  public Result<java.util.List<EscortOptionCatalog>, DomainError> getEscortCatalogPage(
      long guildId, int page, int pageSize) {
    try {
      List<EscortOptionCatalog> all = escortOptionCatalogRepository.findAll();
      int totalItems = all.size();
      int fromIndex = Math.min(page * pageSize, totalItems);
      int toIndex = Math.min(fromIndex + pageSize, totalItems);
      List<EscortOptionCatalog> pageItems =
          fromIndex < totalItems ? all.subList(fromIndex, toIndex) : List.of();
      return Result.ok(pageItems);
    } catch (Exception e) {
      LOG.error("Failed to get escort catalog page: guildId={}, page={}", guildId, page, e);
      return Result.err(DomainError.persistenceFailure("查詢護航價目表失敗", e));
    }
  }

  /** Returns the total count of catalog entries. */
  public long getEscortCatalogCount() {
    try {
      return escortOptionCatalogRepository.count();
    } catch (Exception e) {
      LOG.error("Failed to count escort catalog entries", e);
      return 0;
    }
  }

  /** Creates a new escort catalog entry. */
  public Result<EscortOptionCatalog, DomainError> createEscortCatalogItem(
      String code, String type, String level, String mapScope, String target, long priceTwd) {
    try {
      if (escortOptionCatalogRepository.existsByCode(code.trim().toUpperCase())) {
        return Result.err(DomainError.invalidInput("護航選項代碼已存在：" + code.trim().toUpperCase()));
      }
      EscortOptionCatalog catalog =
          EscortOptionCatalog.create(
              code.trim().toUpperCase(), type, level, mapScope, target, priceTwd);
      EscortOptionCatalog saved = escortOptionCatalogRepository.save(catalog);
      LOG.info("Created escort catalog item: code={}", saved.code());
      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error("Failed to create escort catalog item: code={}", code, e);
      return Result.err(DomainError.persistenceFailure("新增護航項目失敗", e));
    }
  }

  /** Updates an existing escort catalog entry. */
  public Result<EscortOptionCatalog, DomainError> updateEscortCatalogItem(
      String originalCode,
      String code,
      String type,
      String level,
      String mapScope,
      String target,
      long priceTwd) {
    try {
      String normalizedOriginalCode = originalCode.trim().toUpperCase();
      String normalizedNewCode = code.trim().toUpperCase();

      // If code changed, check uniqueness of new code
      if (!normalizedOriginalCode.equals(normalizedNewCode)
          && escortOptionCatalogRepository.existsByCode(normalizedNewCode)) {
        return Result.err(DomainError.invalidInput("護航選項代碼已存在：" + normalizedNewCode));
      }

      EscortOptionCatalog existing =
          escortOptionCatalogRepository.findByCode(normalizedOriginalCode).orElse(null);
      if (existing == null) {
        return Result.err(DomainError.invalidInput("護航選項不存在：" + normalizedOriginalCode));
      }

      EscortOptionCatalog updated =
          existing.withUpdatedDetails(normalizedNewCode, type, level, mapScope, target, priceTwd);
      EscortOptionCatalog saved = escortOptionCatalogRepository.update(updated);
      LOG.info("Updated escort catalog item: code={}", saved.code());
      return Result.ok(saved);
    } catch (Exception e) {
      LOG.error("Failed to update escort catalog item: code={}", code, e);
      return Result.err(DomainError.persistenceFailure("更新護航項目失敗", e));
    }
  }

  /** Deletes a catalog entry by code. Refuses deletion if any product still references it. */
  public Result<Unit, DomainError> deleteEscortCatalogItem(String code) {
    try {
      String normalizedCode = code.trim().toUpperCase();

      if (productRepository != null) {
        long refCount = productRepository.countByEscortOptionCode(normalizedCode);
        if (refCount > 0) {
          return Result.err(
              DomainError.invalidInput(
                  "無法刪除：有 " + refCount + " 個商品仍引用此護航選項（" + normalizedCode + "），請先移除商品引用再刪除"));
        }
      }

      boolean deleted = escortOptionCatalogRepository.deleteByCode(normalizedCode);
      if (!deleted) {
        return Result.err(DomainError.invalidInput("護航選項不存在：" + normalizedCode));
      }
      LOG.info("Deleted escort catalog item: code={}", normalizedCode);
      return Result.okVoid();
    } catch (Exception e) {
      LOG.error("Failed to delete escort catalog item: code={}", code, e);
      return Result.err(DomainError.persistenceFailure("刪除護航項目失敗", e));
    }
  }

  /** Checks if a catalog entry with the given code exists. */
  public boolean existsEscortCatalogItemByCode(String code) {
    try {
      return escortOptionCatalogRepository.existsByCode(code.trim().toUpperCase());
    } catch (Exception e) {
      return false;
    }
  }
}
