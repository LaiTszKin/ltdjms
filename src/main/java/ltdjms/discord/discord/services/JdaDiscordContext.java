package ltdjms.discord.discord.services;

import java.util.Optional;

import ltdjms.discord.discord.domain.DiscordContext;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

/**
 * JDA 實作的 Discord 事件上下文包裝器
 *
 * <p>此類別將 JDA 的 {@link GenericInteractionCreateEvent} 包裝為統一的 {@link DiscordContext} 介面，提供與 JDA
 * 實作細節無關的抽象層。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>從 JDA 事件中提取 Guild、使用者、頻道 ID
 *   <li>取得使用者 Mention 格式
 *   <li>取得命令參數（options）並進行類型轉換
 * </ul>
 *
 * <p>支援的事件類型：
 *
 * <ul>
 *   <li>SlashCommandInteractionEvent
 *   <li>其他實現 getOptionByName() 的互動事件
 * </ul>
 */
public class JdaDiscordContext implements DiscordContext {

  private final GenericInteractionCreateEvent event;

  /**
   * 建構 JDA Discord 事件上下文包裝器
   *
   * @param event JDA 互動事件
   * @throws IllegalArgumentException 如果事件不支援 getOptionByName()
   */
  public JdaDiscordContext(GenericInteractionCreateEvent event) {
    this.event = event;
    if (!supportsOptions(event)) {
      throw new IllegalArgumentException(
          "不支援的事件類型: " + event.getClass().getSimpleName() + "。此事件不支援 getOption() 方法。");
    }
  }

  /**
   * 檢查事件是否支援選項提取
   *
   * <p>只有特定類型的事件（如 SlashCommandInteractionEvent）才支援 getOption()。
   *
   * @param event JDA 互動事件
   * @return true 如果支援選項提取
   */
  private boolean supportsOptions(GenericInteractionCreateEvent event) {
    // 使用反射檢查事件是否有 getOption 方法
    try {
      event.getClass().getMethod("getOption", String.class);
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * 使用反射從事件中取得選項
   *
   * <p>由於 GenericInteractionCreateEvent 沒有 getOption() 方法， 我們使用反射來動態調用此方法。
   *
   * @param name 選項名稱
   * @return OptionMapping 物件，如果選項不存在則為 null
   */
  private OptionMapping getOptionFromEvent(String name) {
    try {
      var method = event.getClass().getMethod("getOption", String.class);
      return (OptionMapping) method.invoke(event, name);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public long getGuildId() {
    // 在 DM 互動中，Guild 可能為 null
    if (event.getGuild() == null) {
      return 0L;
    }
    return event.getGuild().getIdLong();
  }

  @Override
  public long getUserId() {
    return event.getUser().getIdLong();
  }

  @Override
  public long getChannelId() {
    return event.getChannel().getIdLong();
  }

  @Override
  public String getUserMention() {
    return event.getUser().getAsMention();
  }

  @Override
  public Optional<String> getOption(String name) {
    OptionMapping option = getOptionFromEvent(name);
    if (option == null) {
      return Optional.empty();
    }
    return Optional.of(option.getAsString());
  }

  @Override
  public Optional<String> getOptionAsString(String name) {
    OptionMapping option = getOptionFromEvent(name);
    if (option == null) {
      return Optional.empty();
    }
    // 只有字串類型的選項才返回
    if (option.getType() == OptionType.STRING) {
      return Optional.of(option.getAsString());
    }
    return Optional.empty();
  }

  @Override
  public Optional<Long> getOptionAsLong(String name) {
    OptionMapping option = getOptionFromEvent(name);
    if (option == null) {
      return Optional.empty();
    }
    // 只有整數類型的選項才返回
    if (option.getType() == OptionType.INTEGER) {
      return Optional.of(option.getAsLong());
    }
    return Optional.empty();
  }

  @Override
  public Optional<User> getOptionAsUser(String name) {
    OptionMapping option = getOptionFromEvent(name);
    if (option == null) {
      return Optional.empty();
    }
    // 只有使用者或會員類型的選項才返回
    if (option.getType() == OptionType.USER || option.getType() == OptionType.MENTIONABLE) {
      return Optional.ofNullable(option.getAsUser());
    }
    return Optional.empty();
  }
}
