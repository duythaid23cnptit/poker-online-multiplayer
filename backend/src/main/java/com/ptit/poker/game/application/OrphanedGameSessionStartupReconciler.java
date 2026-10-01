package com.ptit.poker.game.application;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("!bootstrap")
public class OrphanedGameSessionStartupReconciler implements SmartInitializingSingleton {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrphanedGameSessionStartupReconciler.class);
    private final OrphanedGameSessionReconciliationService reconciliation;

    public OrphanedGameSessionStartupReconciler(OrphanedGameSessionReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Override
    public void afterSingletonsInstantiated() {
        var result = reconciliation.reconcile();
        if (result.sessionsAborted() > 0) {
            LOGGER.warn("Reconciled orphaned game sessions: sessionsAborted={}, roomsFinished={}, membershipsFinalized={}, chipsRefunded={}",
                    result.sessionsAborted(), result.roomsFinished(), result.membershipsFinalized(), result.chipsRefunded());
        }
    }
}
