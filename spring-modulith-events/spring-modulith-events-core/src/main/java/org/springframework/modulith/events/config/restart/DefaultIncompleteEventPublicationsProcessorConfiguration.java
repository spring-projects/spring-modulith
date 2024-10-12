package org.springframework.modulith.events.config.restart;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.support.restart.DefaultIncompleteEventPublicationsProcessor;
import org.springframework.modulith.events.support.restart.IncompleteEventPublicationsProcessor;

import java.util.Optional;

/**
 * Default behavior configured to resubmit all incomplete events.
 *
 * @author Josh Long
 */
@AutoConfiguration
@AutoConfigureAfter(ExclusiveIncompleteEventPublicationsProcessorConfiguration.class)
class DefaultIncompleteEventPublicationsProcessorConfiguration {

    static final String REPUBLISH_ON_RESTART = "spring.modulith.events.republish-outstanding-events-on-restart";

    static final String REPUBLISH_ON_RESTART_LEGACY = "spring.modulith.republish-outstanding-events-on-restart";

    @Bean
    @ConditionalOnBean(IncompleteEventPublications.class)
    @ConditionalOnMissingBean(IncompleteEventPublicationsProcessor.class)
    DefaultIncompleteEventPublicationsProcessor defaultIncompleteEventPublicationsProcessor(IncompleteEventPublications publications, Environment environment) {
        var republishOnRestart = Optional.ofNullable(environment.getProperty(REPUBLISH_ON_RESTART, Boolean.class))
                .orElseGet(() -> environment.getProperty(REPUBLISH_ON_RESTART_LEGACY, Boolean.class));
        return new DefaultIncompleteEventPublicationsProcessor(
                Boolean.TRUE.equals(republishOnRestart), publications);
    }
}
