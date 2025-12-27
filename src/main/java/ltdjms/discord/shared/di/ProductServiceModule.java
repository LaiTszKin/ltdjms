package ltdjms.discord.shared.di;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.domain.ProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.services.ProductRedemptionTransactionService;
import ltdjms.discord.redemption.services.RedemptionCodeGenerator;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Dagger module providing product and redemption service dependencies. */
@Module
public class ProductServiceModule {

  @Provides
  @Singleton
  public ProductService provideProductService(
      ProductRepository productRepository,
      RedemptionCodeRepository redemptionCodeRepository,
      DomainEventPublisher eventPublisher) {
    return new ProductService(productRepository, redemptionCodeRepository, eventPublisher);
  }

  @Provides
  @Singleton
  public ProductRedemptionTransactionService provideProductRedemptionTransactionService(
      ProductRedemptionTransactionRepository productRedemptionTransactionRepository) {
    return new ProductRedemptionTransactionService(productRedemptionTransactionRepository);
  }

  @Provides
  @Singleton
  public RedemptionCodeGenerator provideRedemptionCodeGenerator() {
    return new RedemptionCodeGenerator();
  }

  @Provides
  @Singleton
  public RedemptionService provideRedemptionService(
      RedemptionCodeRepository codeRepository,
      ProductRepository productRepository,
      RedemptionCodeGenerator codeGenerator,
      BalanceAdjustmentService balanceAdjustmentService,
      GameTokenService gameTokenService,
      CurrencyTransactionService currencyTransactionService,
      GameTokenTransactionService gameTokenTransactionService,
      ProductRedemptionTransactionService productRedemptionTransactionService,
      DomainEventPublisher eventPublisher) {
    return new RedemptionService(
        codeRepository,
        productRepository,
        codeGenerator,
        balanceAdjustmentService,
        gameTokenService,
        currencyTransactionService,
        gameTokenTransactionService,
        productRedemptionTransactionService,
        eventPublisher);
  }
}
