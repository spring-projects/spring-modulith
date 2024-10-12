/*
 * Copyright 2017-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

