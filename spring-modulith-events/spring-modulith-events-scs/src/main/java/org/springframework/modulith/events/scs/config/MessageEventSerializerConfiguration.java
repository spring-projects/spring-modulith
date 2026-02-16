package org.springframework.modulith.events.scs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.scs.AvroEventSerializer;
import org.springframework.modulith.events.scs.MessageEventSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.core.EventSerializer;

@Configuration
@AutoConfigureAfter(EventExternalizationAutoConfiguration.class)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true",
        matchIfMissing = true)
public class MessageEventSerializerConfiguration {

    @Bean
    @Primary
    @ConditionalOnClass(AvroMapper.class)
    public EventSerializer avroEventSerializer(ObjectMapper mapper) {
        return new AvroEventSerializer(mapper);
    }

    @Bean
    @Primary
    @ConditionalOnMissingClass("com.fasterxml.jackson.dataformat.avro.AvroMapper")
    public EventSerializer messageEventSerializer(ObjectMapper mapper) {
        return new MessageEventSerializer(mapper);
    }

}
