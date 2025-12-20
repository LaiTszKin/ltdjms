package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.services.RedemptionCodeGenerator;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.events.DomainEventPublisher;

import javax.inject.Singleton;

/**
 * Dagger module providing product and redemption service dependencies.
 */
@Module
public class ProductServiceModule {

    @Provides
    @Singleton
    public ProductService provideProductService(
            ProductRepository productRepository,
            DomainEventPublisher eventPublisher) {
        return new ProductService(productRepository, eventPublisher);
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
            DomainEventPublisher eventPublisher) {
        return new RedemptionService(
                codeRepository,
                productRepository,
                codeGenerator,
                balanceAdjustmentService,
                gameTokenService,
                currencyTransactionService,
                gameTokenTransactionService,
                eventPublisher);
    }
}
