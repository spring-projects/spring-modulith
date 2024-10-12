package org.springframework.modulith.events.support;

import org.springframework.core.env.Environment;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * uses Spring Integration's {@link LockRegistry} to obtain
 * an exclusive, cluster-wide lock before resubmitting incomplete event publications.
 *
 * @author Josh Long
 */

class ExclusiveIncompleteEventPublicationsProcessor extends DefaultIncompleteEventPublicationsProcessor {

    static final String REPUBLISH_ON_RESTART_LOCK = "spring.modulith.events.republish-outstanding-events-on-restart.lock-name";
    static final String REPUBLISH_ON_RESTART_TIMEOUT_IN_MILLISECONDS = "spring.modulith.events.republish-outstanding-events-on-restart.lock-timeout";

    private final Supplier<LockRegistry> lockRegistrySupplier;
    private final Supplier<Environment> environmentSupplier;

    ExclusiveIncompleteEventPublicationsProcessor(
            Supplier<LockRegistry> lockRegistrySupplier,
            Supplier<Environment> environment, Supplier<IncompleteEventPublications> incompleteEventPublicationsSupplier) {
        super(environment, incompleteEventPublicationsSupplier);
        this.lockRegistrySupplier = lockRegistrySupplier;
        this.environmentSupplier = environment;
    }

    @Override
    public void process() throws Throwable {
        var environment = this.environmentSupplier.get();
        var lockRegistry = this.lockRegistrySupplier.get();
        var lockName = environment.getProperty(REPUBLISH_ON_RESTART_LOCK);
        var timeoutInMilliseconds = (long) environment.getProperty(
                REPUBLISH_ON_RESTART_TIMEOUT_IN_MILLISECONDS, Long.class, -1L);
        lockRegistry.executeLocked(lockName, Duration.ofMillis(timeoutInMilliseconds == -1? 1_000 : timeoutInMilliseconds), () -> {
            super.process();
            return null;
        });
    }


}
