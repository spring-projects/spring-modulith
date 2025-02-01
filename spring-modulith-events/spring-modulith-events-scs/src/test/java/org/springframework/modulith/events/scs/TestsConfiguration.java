package org.springframework.modulith.events.scs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.modulith.events.scs.config.EnableSpringCloudStreamEventExternalization;
import org.springframework.modulith.events.scs.dtos.avro.CustomerEvent;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaZKBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@EnableAutoConfiguration
@EnableSpringCloudStreamEventExternalization
@EmbeddedKafka(partitions = 1)
@EnableTransactionManagement
public class TestsConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper(); // Customize if needed
    }

    @Bean
    EmbeddedKafkaBroker embeddedKafkaBroker() {
        return new EmbeddedKafkaZKBroker(1, true, 1);
    }

    @Bean
    CustomerEventsProducer customerEventsProducer(ApplicationEventPublisher applicationEventPublisher) {
        return new CustomerEventsProducer(applicationEventPublisher);
    }

    static class CustomerEventsProducer {

        private final ApplicationEventPublisher applicationEventPublisher;

        public CustomerEventsProducer(ApplicationEventPublisher applicationEventPublisher) {
            this.applicationEventPublisher = applicationEventPublisher;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void onCustomerEventJsonMessage(org.springframework.modulith.events.scs.dtos.json.CustomerEvent event) {
            Message<org.springframework.modulith.events.scs.dtos.json.CustomerEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(SpringCloudStreamEventExternalizer.SPRING_CLOUD_STREAM_SENDTO_DESTINATION_HEADER,
                        "customers-json-out-0") // <- target binding name
                .build();
            applicationEventPublisher.publishEvent(message);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void onCustomerEventJsonPojo(org.springframework.modulith.events.scs.dtos.json.CustomerEvent event) {
            applicationEventPublisher.publishEvent(event);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void onCustomerEventAvroMessage(CustomerEvent event) {
            Message<CustomerEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(SpringCloudStreamEventExternalizer.SPRING_CLOUD_STREAM_SENDTO_DESTINATION_HEADER,
                        "customers-avro-out-0") // <- target binding name
                .build();
            applicationEventPublisher.publishEvent(message);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void onCustomerEventAvroPojo(CustomerEvent event) {
            applicationEventPublisher.publishEvent(event);
        }

    }

}
