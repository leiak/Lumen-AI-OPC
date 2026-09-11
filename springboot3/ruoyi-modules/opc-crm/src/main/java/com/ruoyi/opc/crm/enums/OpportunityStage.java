package com.ruoyi.opc.crm.enums;

import java.util.Set;

/**
 * 商机阶段 (漏斗: LEAD → QUALIFIED → PROPOSAL → NEGOTIATION → WON/LOST)
 */
public enum OpportunityStage {
    LEAD, QUALIFIED, PROPOSAL, NEGOTIATION, WON, LOST;

    private static final Set<OpportunityStage> FROM_LEAD       = Set.of(QUALIFIED, LOST);
    private static final Set<OpportunityStage> FROM_QUALIFIED  = Set.of(PROPOSAL, LOST);
    private static final Set<OpportunityStage> FROM_PROPOSAL   = Set.of(NEGOTIATION, LOST);
    private static final Set<OpportunityStage> FROM_NEGOTIATION = Set.of(WON, LOST);
    private static final Set<OpportunityStage> FROM_WON        = Set.of();
    private static final Set<OpportunityStage> FROM_LOST       = Set.of(LEAD);

    public boolean canTransitionTo(OpportunityStage target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case LEAD        -> FROM_LEAD.contains(target);
            case QUALIFIED   -> FROM_QUALIFIED.contains(target);
            case PROPOSAL    -> FROM_PROPOSAL.contains(target);
            case NEGOTIATION -> FROM_NEGOTIATION.contains(target);
            case WON         -> FROM_WON.contains(target);
            case LOST        -> FROM_LOST.contains(target);
        };
    }

    public boolean isTerminal() {
        return this == WON;
    }

    /** 是否计入漏斗 (WON/LOST 不计入活跃漏斗) */
    public boolean isActive() {
        return this != WON && this != LOST;
    }
}
