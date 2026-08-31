package com.ptit.poker.social.presence.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!bootstrap")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PresenceStartupReconciler implements ApplicationRunner {
    private final PresencePersistencePort presence;

    public PresenceStartupReconciler(PresencePersistencePort presence) { this.presence = presence; }

    @Override @Transactional
    public void run(ApplicationArguments args) { reconcile(); }

    public int reconcile() { return presence.resetAllOffline(); }
}
