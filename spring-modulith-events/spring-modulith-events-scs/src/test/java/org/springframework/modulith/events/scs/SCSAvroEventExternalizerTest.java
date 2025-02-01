package org.springframework.modulith.events.scs;

import org.springframework.modulith.events.scs.dtos.avro.CustomerEvent;
import org.springframework.modulith.events.scs.dtos.avro.ExternalizedCustomerEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = { TestsConfiguration.class })
@Transactional
public class SCSAvroEventExternalizerTest {

    @Autowired
    TestsConfiguration.CustomerEventsProducer customerEventsProducer;

    @MockitoSpyBean
    private StreamBridge streamBridge;

    @Test
    void testExternalizeAvroEvent() throws InterruptedException {
        var event = new CustomerEvent();
        event.setName("John Doe");

        customerEventsProducer.onCustomerEventAvroMessage(event);

        // Wait for the event to be externalized
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(streamBridge).send(Mockito.eq("customers-avro-out-0"), Mockito.argThat(message -> {
                if (message instanceof Message<?>) {
                    var payload = ((Message<?>) message).getPayload();
                    return payload instanceof CustomerEvent
                            && "John Doe".equals(((CustomerEvent) payload).getName());
                }
                return false;
            }));
        });
    }

    @Test
    void testExternalizeAvroPojo() throws InterruptedException {
        var event = new ExternalizedCustomerEvent();
        event.setName("John Doe Externalized");
        customerEventsProducer.onCustomerEventAvroPojo(event);

        // Wait for the event to be externalized
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(streamBridge).send(Mockito.eq("customers-avro-externalized-out-0"), Mockito.argThat(message -> {
                if (message instanceof Message<?>) {
                    var payload = ((Message<?>) message).getPayload();
                    return payload instanceof CustomerEvent
                            && "John Doe Externalized".equals(((CustomerEvent) payload).getName());
                }
                return false;
            }));
        });
    }

}
