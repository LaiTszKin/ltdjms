package ltdjms.discord.membership.di;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import javax.inject.Qualifier;

/** Qualifier for Asia/Taipei settlement clock used by membership and panel period logic. */
@Qualifier
@Documented
@Retention(RUNTIME)
public @interface SettlementClock {}
