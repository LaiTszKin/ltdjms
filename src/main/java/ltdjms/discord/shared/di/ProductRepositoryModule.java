package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.redemption.domain.ProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.persistence.JdbcProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.persistence.JdbcRedemptionCodeRepository;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Dagger module providing product and redemption code repository dependencies.
 */
@Module
public class ProductRepositoryModule {

    @Provides
    @Singleton
    public ProductRepository provideProductRepository(DataSource dataSource) {
        return new JdbcProductRepository(dataSource);
    }

    @Provides
    @Singleton
    public RedemptionCodeRepository provideRedemptionCodeRepository(DataSource dataSource) {
        return new JdbcRedemptionCodeRepository(dataSource);
    }

    @Provides
    @Singleton
    public ProductRedemptionTransactionRepository provideProductRedemptionTransactionRepository(DataSource dataSource) {
        return new JdbcProductRedemptionTransactionRepository(dataSource);
    }
}
