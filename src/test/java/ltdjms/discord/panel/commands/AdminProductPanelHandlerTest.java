package ltdjms.discord.panel.commands;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ltdjms.discord.panel.services.AdminPanelSessionManager;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.redemption.domain.RedemptionCodeRepository;
import ltdjms.discord.redemption.services.RedemptionService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;

/** 驗證商品面板的即時刷新邏輯會觸發對原訊息的更新。 */
class AdminProductPanelHandlerTest {

  private AdminPanelSessionManager sessionManager;
  private InteractionHook hook;
  private WebhookMessageEditAction<Message> editAction;
  private AdminProductPanelHandler handler;
  private RedemptionService redemptionService;
  private ltdjms.discord.product.services.ProductService productService;

  private final long guildId = 100L;
  private final long adminId = 200L;
  private final long productId = 300L;

  @BeforeEach
  void setUp() {
    sessionManager = new AdminPanelSessionManager();
    hook = mock(InteractionHook.class);
    editAction = mockEditAction();

    when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
    when(editAction.setComponents(anyLayoutComponents())).thenReturn(editAction);
    doAnswer(invocation -> null).when(editAction).queue(any(), any());

    sessionManager.registerSession(guildId, adminId, hook);

    productService = mock(ltdjms.discord.product.services.ProductService.class);
    var product =
        new Product(
            Long.valueOf(productId),
            guildId,
            "Test Product",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());
    when(productService.getProduct(productId)).thenReturn(Optional.of(product));
    when(productService.getProducts(guildId)).thenReturn(List.of(product));

    redemptionService = mock(RedemptionService.class);
    when(redemptionService.getCodeStats(productId))
        .thenReturn(new RedemptionCodeRepository.CodeStats(0, 0, 0, 0));
    when(redemptionService.getCodePage(eq(productId), anyInt(), anyInt()))
        .thenReturn(new RedemptionService.CodePage(List.of(), 1, 1, 0, 10));

    handler = new AdminProductPanelHandler(productService, redemptionService, sessionManager);
  }

  @Test
  void refreshProductPanels_shouldEditMessage_whenAdminHasProductSession() {
    handler.setProductSessionForTest(
        adminId, guildId, AdminProductPanelHandler.ProductView.DETAIL, productId, 1);

    handler.refreshProductPanels(guildId);

    verify(hook, atLeastOnce()).editOriginalEmbeds(any(MessageEmbed.class));
    verify(editAction, atLeastOnce()).setComponents(anyLayoutComponents());
  }

  @Test
  void refreshProductPanels_shouldRevertToList_whenProductRemoved() {
    handler.setProductSessionForTest(
        adminId, guildId, AdminProductPanelHandler.ProductView.DETAIL, 999L, 1);

    handler.refreshProductPanels(guildId);

    verify(hook)
        .editOriginalEmbeds(argThat((MessageEmbed embed) -> "📦 商品管理".equals(embed.getTitle())));
  }

  @Test
  void createProductModal_shouldRefreshProductList() {
    var event = mock(net.dv8tion.jda.api.events.interaction.ModalInteractionEvent.class);
    var guild = mock(net.dv8tion.jda.api.entities.Guild.class);
    var admin = mock(net.dv8tion.jda.api.entities.User.class);
    var adminMember = mock(net.dv8tion.jda.api.entities.Member.class);
    var reply =
        mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction.class);
    var nameMapping = mock(net.dv8tion.jda.api.interactions.modals.ModalMapping.class);

    when(event.getModalId()).thenReturn(AdminProductPanelHandler.MODAL_CREATE_PRODUCT);
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(guildId);
    when(event.getMember()).thenReturn(adminMember);
    when(adminMember.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(event.getUser()).thenReturn(admin);
    when(admin.getIdLong()).thenReturn(adminId);

    when(event.getValue("name")).thenReturn(nameMapping);
    when(nameMapping.getAsString()).thenReturn("New Product");
    when(event.getValue("description")).thenReturn(null);
    when(event.getValue("reward_type")).thenReturn(null);
    when(event.getValue("reward_amount")).thenReturn(null);

    when(event.reply(anyString())).thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);
    doAnswer(invocation -> null).when(reply).queue();

    var newProduct =
        new Product(
            Long.valueOf(productId),
            guildId,
            "New Product",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());
    when(productService.createProduct(eq(guildId), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(ltdjms.discord.shared.Result.ok(newProduct));
    when(productService.getProducts(guildId)).thenReturn(List.of(newProduct));
    when(redemptionService.getCodeStats(newProduct.id()))
        .thenReturn(new RedemptionCodeRepository.CodeStats(0, 0, 0, 0));

    handler.onModalInteraction(event);

    verify(hook, atLeastOnce()).editOriginalEmbeds(any(MessageEmbed.class));
  }

  @Test
  void setFiatValueModal_shouldUpdateProductFiatPrice() {
    var event = mock(net.dv8tion.jda.api.events.interaction.ModalInteractionEvent.class);
    var guild = mock(net.dv8tion.jda.api.entities.Guild.class);
    var adminMember = mock(net.dv8tion.jda.api.entities.Member.class);
    var reply =
        mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction.class);
    var fiatPriceMapping = mock(net.dv8tion.jda.api.interactions.modals.ModalMapping.class);

    when(event.getModalId()).thenReturn(AdminProductPanelHandler.MODAL_SET_FIAT_VALUE + productId);
    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(guild.getIdLong()).thenReturn(guildId);
    when(event.getMember()).thenReturn(adminMember);
    when(adminMember.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
    when(event.getValue("fiat_price_twd")).thenReturn(fiatPriceMapping);
    when(fiatPriceMapping.getAsString()).thenReturn("1200");

    when(event.reply(anyString())).thenReturn(reply);
    when(reply.setEphemeral(true)).thenReturn(reply);
    doAnswer(invocation -> null).when(reply).queue();

    var updatedProduct =
        new Product(
            Long.valueOf(productId),
            guildId,
            "Test Product",
            null,
            null,
            null,
            null,
            1200L,
            Instant.now(),
            Instant.now());
    when(productService.updateProduct(eq(productId), any(), any(), any(), any(), any(), eq(1200L)))
        .thenReturn(ltdjms.discord.shared.Result.ok(updatedProduct));

    handler.onModalInteraction(event);

    verify(productService).updateProduct(productId, "Test Product", null, null, null, null, 1200L);
    verify(event).reply("✅ 已更新實際價值（TWD）");
  }

  @SuppressWarnings("unchecked")
  private static WebhookMessageEditAction<Message> mockEditAction() {
    return mock(WebhookMessageEditAction.class);
  }

  private static List<? extends LayoutComponent> anyLayoutComponents() {
    return anyList();
  }
}
