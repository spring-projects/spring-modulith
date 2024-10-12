package org.springframework.modulith.events.support;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.util.Optional;
import java.util.function.Supplier;


/**
 * The default implementation when {@code spring.modulith.events.republish-outstanding-events-on-restart} is set to true.
 * 
 * @author Oliver Drotbohm
 * @author Josh Long
 */
public class DefaultIncompleteEventPublicationsProcessor implements ApplicationRunner {

    static final String REPUBLISH_ON_RESTART = "spring.modulith.events.republish-outstanding-events-on-restart";

    static final String REPUBLISH_ON_RESTART_LEGACY = "spring.modulith.republish-outstanding-events-on-restart";
    
    private final Supplier<Environment> environment;

    private final Supplier<IncompleteEventPublications> incompleteEventPublicationsSupplier;

    DefaultIncompleteEventPublicationsProcessor(Supplier<Environment> environment, Supplier<IncompleteEventPublications> incompleteEventPublicationsSupplier) {
        this.environment = environment;
        this.incompleteEventPublicationsSupplier = incompleteEventPublicationsSupplier;
    }


    // todo can we live with ApplicationRunner#run? or does it have to 
    //  be SmartInitializingSingleton, which runs, notably, before the Boot Actuator health check is healthy and 
    //  could prevent the app from starting up in time? 
    @Override
    public void run(ApplicationArguments args) throws Exception {


        var env = environment.get();

        var republishOnRestart = Optional.ofNullable(env.getProperty(REPUBLISH_ON_RESTART, Boolean.class))
                .orElseGet(() -> env.getProperty(REPUBLISH_ON_RESTART_LEGACY, Boolean.class));

        if (!Boolean.TRUE.equals(republishOnRestart)) {
            return;
        }
        try {
            this.process();
        }// 
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    protected void process() throws Throwable {
        this.incompleteEventPublicationsSupplier.get().resubmitIncompletePublications(a -> true);
    }
}

