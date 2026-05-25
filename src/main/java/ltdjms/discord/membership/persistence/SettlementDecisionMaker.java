package ltdjms.discord.membership.persistence;

/** Computes settlement tier changes from locked membership state and period spend. */
@FunctionalInterface
public interface SettlementDecisionMaker {

  SettlementDecision decide(SettlementContext context);
}
