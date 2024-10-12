package org.springframework.modulith.events.support.restart;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.modulith.events.IncompleteEventPublications;


/**
 * The default implementation when {@code spring.modulith.events.republish-outstanding-events-on-restart} is set to true.
 *
 * @author Oliver Drotbohm
 * @author Josh Long
 */
public class DefaultIncompleteEventPublicationsProcessor implements
        IncompleteEventPublicationsProcessor, ApplicationRunner {

    private final boolean republishOnRestart;
    private final IncompleteEventPublications publications;

    public DefaultIncompleteEventPublicationsProcessor(boolean republishOnRestart, IncompleteEventPublications publications) {
        this.republishOnRestart = republishOnRestart;
        this.publications = publications;
    }

    // todo can we live with ApplicationRunner#run? or does it have to 
    //  be SmartInitializingSingleton, which runs, notably, before the Boot Actuator health check is healthy and 
    //  could prevent the app from starting up in time? 
    @Override
    public void run(ApplicationArguments args) throws Exception {

        if (!republishOnRestart) {
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
        this.publications.resubmitIncompletePublications(a -> true);
    }
}

