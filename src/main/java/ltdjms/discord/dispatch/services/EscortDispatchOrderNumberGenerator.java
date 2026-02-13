package ltdjms.discord.dispatch.services;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;

/** 護航派單訂單編號產生器。格式：ESC-YYYYMMDD-XXXXXX */
public class EscortDispatchOrderNumberGenerator {

  private static final String PREFIX = "ESC";
  private static final int SUFFIX_LENGTH = 6;
  private static final char[] ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  private final Clock clock;
  private final Random random;

  public EscortDispatchOrderNumberGenerator() {
    this(Clock.systemUTC(), new SecureRandom());
  }

  EscortDispatchOrderNumberGenerator(Clock clock, Random random) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  public String generate() {
    String datePart = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
    return PREFIX + "-" + datePart + "-" + randomSuffix();
  }

  private String randomSuffix() {
    StringBuilder builder = new StringBuilder(SUFFIX_LENGTH);
    for (int i = 0; i < SUFFIX_LENGTH; i++) {
      builder.append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)]);
    }
    return builder.toString();
  }
}
