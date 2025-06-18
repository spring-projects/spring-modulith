package org.springframework.modulith.events.scs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Configuration
@Import({ SpringCloudStreamEventExternalizerConfiguration.class, MessageEventSerializerConfiguration.class,
        MessageExternalizationConfiguration.class })
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableSpringCloudStreamEventExternalization {

}
