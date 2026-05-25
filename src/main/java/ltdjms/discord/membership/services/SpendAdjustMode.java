package ltdjms.discord.membership.services;

/** Admin adjustment mode for membership period spend M. */
public enum SpendAdjustMode {
  ADD,
  DEDUCT,
  SET;

  /** Parses panel mode strings: add, deduct, set. */
  public static SpendAdjustMode fromPanelMode(String mode) {
    if (mode == null || mode.isBlank()) {
      throw new IllegalArgumentException("mode must not be blank");
    }
    return switch (mode.trim().toLowerCase()) {
      case "add" -> ADD;
      case "deduct" -> DEDUCT;
      case "set", "adjust" -> SET;
      default -> throw new IllegalArgumentException("Unknown spend adjust mode: " + mode);
    };
  }
}
