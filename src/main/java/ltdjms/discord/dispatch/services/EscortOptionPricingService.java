package ltdjms.discord.dispatch.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.dispatch.domain.EscortOptionPriceRepository;
import ltdjms.discord.product.domain.EscortOrderOptionCatalog;
import ltdjms.discord.product.domain.EscortOrderOptionCatalog.EscortOrderOption;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** Service for guild-level escort option pricing overrides. */
public class EscortOptionPricingService {

  private static final Logger LOG = LoggerFactory.getLogger(EscortOptionPricingService.class);

  private final EscortOptionPriceRepository repository;

  public EscortOptionPricingService(EscortOptionPriceRepository repository) {
    this.repository = repository;
  }

  public Result<List<OptionPriceView>, DomainError> listOptionPrices(long guildId) {
    try {
      Map<String, Long> overrides = repository.findAllByGuildId(guildId);
      List<OptionPriceView> prices = new ArrayList<>();

      for (EscortOrderOption option : EscortOrderOptionCatalog.allOptions()) {
        Long override = overrides.get(option.code());
        long effective = override != null ? override : option.priceTwd();
        prices.add(
            new OptionPriceView(
                option.code(), option, option.priceTwd(), effective, override != null));
      }
      return Result.ok(prices);
    } catch (Exception e) {
      LOG.error("Failed to list escort option prices: guildId={}", guildId, e);
      return Result.err(DomainError.persistenceFailure("查詢護航定價失敗", e));
    }
  }

  public Result<OptionPriceView, DomainError> updateOptionPrice(
      long guildId, long updatedByUserId, String optionCode, long priceTwd) {
    if (priceTwd <= 0) {
      return Result.err(DomainError.invalidInput("護航價格必須大於 0"));
    }
    if (optionCode == null || optionCode.isBlank()) {
      return Result.err(DomainError.invalidInput("護航選項代碼不能為空"));
    }
    String normalizedCode = optionCode.trim().toUpperCase();
    EscortOrderOption option = EscortOrderOptionCatalog.findByCode(normalizedCode).orElse(null);
    if (option == null) {
      return Result.err(
          DomainError.invalidInput(
              "護航選項代碼無效，可用代碼：" + EscortOrderOptionCatalog.supportedCodesForDisplay()));
    }

    try {
      repository.upsert(guildId, normalizedCode, priceTwd, updatedByUserId);
      return Result.ok(
          new OptionPriceView(normalizedCode, option, option.priceTwd(), priceTwd, true));
    } catch (Exception e) {
      LOG.error(
          "Failed to update escort option price: guildId={}, optionCode={}, priceTwd={}",
          guildId,
          normalizedCode,
          priceTwd,
          e);
      return Result.err(DomainError.persistenceFailure("更新護航定價失敗", e));
    }
  }

  public Result<Unit, DomainError> resetOptionPrice(long guildId, String optionCode) {
    if (optionCode == null || optionCode.isBlank()) {
      return Result.err(DomainError.invalidInput("護航選項代碼不能為空"));
    }
    String normalizedCode = optionCode.trim().toUpperCase();
    if (!EscortOrderOptionCatalog.isSupported(normalizedCode)) {
      return Result.err(
          DomainError.invalidInput(
              "護航選項代碼無效，可用代碼：" + EscortOrderOptionCatalog.supportedCodesForDisplay()));
    }

    try {
      repository.delete(guildId, normalizedCode);
      return Result.okVoid();
    } catch (Exception e) {
      LOG.error(
          "Failed to reset escort option price: guildId={}, optionCode={}",
          guildId,
          normalizedCode,
          e);
      return Result.err(DomainError.persistenceFailure("重置護航定價失敗", e));
    }
  }

  public Result<Long, DomainError> getEffectivePrice(long guildId, String optionCode) {
    if (optionCode == null || optionCode.isBlank()) {
      return Result.err(DomainError.invalidInput("護航選項代碼不能為空"));
    }
    String normalizedCode = optionCode.trim().toUpperCase();
    EscortOrderOption option = EscortOrderOptionCatalog.findByCode(normalizedCode).orElse(null);
    if (option == null) {
      return Result.err(DomainError.invalidInput("護航選項代碼無效"));
    }

    try {
      Long override = repository.findByGuildIdAndOptionCode(guildId, normalizedCode).orElse(null);
      return Result.ok(override != null ? override : option.priceTwd());
    } catch (Exception e) {
      LOG.error(
          "Failed to get effective escort option price: guildId={}, optionCode={}",
          guildId,
          normalizedCode,
          e);
      return Result.err(DomainError.persistenceFailure("查詢護航定價失敗", e));
    }
  }

  public record OptionPriceView(
      String optionCode,
      EscortOrderOption option,
      long defaultPriceTwd,
      long effectivePriceTwd,
      boolean overridden) {
    public String toDisplayLine() {
      String suffix = overridden ? "（已覆蓋）" : "（預設）";
      return String.format(
          "`%s` %s -> NT$%,d %s", optionCode, option.toDisplayText(), effectivePriceTwd, suffix);
    }
  }
}
