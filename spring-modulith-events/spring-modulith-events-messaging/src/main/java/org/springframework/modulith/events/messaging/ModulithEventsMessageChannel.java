package org.springframework.modulith.events.messaging;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * 
 * Qualifier to signal to which Spring Framework {@link  org.springframework.messaging.MessageChannel message channel}
 * externalized events should be routed. 
 * 
 * @author Josh Long
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier
public @interface ModulithEventsMessageChannel {
}
