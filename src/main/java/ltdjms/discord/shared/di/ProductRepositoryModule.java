package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.product.domain.ProductRepository;
import ltdjms.discord.product.persistence.JdbcProductRepository;
import ltdjms.discord.redemption.domain.ProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.persistence.JdbcProductRedemptionTransactionRepository;
import ltdjms.discord.redemption.persistence.JdbcRedemptionCodeRepository;
import ltdjms.discord.shop.domain.FiatOrderRepository;
import ltdjms.discord.shop.persistence.JdbcFiatOrderRepository;

/** Dagger module providing product and redemption code repository dependencies. */
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
  public ProductRedemptionTransactionRepository provideProductRedemptionTransactionRepository(
      DataSource dataSource) {
    return new JdbcProductRedemptionTransactionRepository(dataSource);
  }

  @Provides
  @Singleton
  public FiatOrderRepository provideFiatOrderRepository(DataSource dataSource) {
    return new JdbcFiatOrderRepository(dataSource);
  }
}
