package org.springframework.modulith.events.scs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.expression.EvaluationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.support.BrokerRouting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class SpringCloudStreamEventExternalizer implements BiFunction<RoutingTarget, Object, CompletableFuture<?>> {

    private static final Logger log = LoggerFactory.getLogger(SpringCloudStreamEventExternalizer.class);

    public static final String SPRING_CLOUD_STREAM_SENDTO_DESTINATION_HEADER = "spring.cloud.stream.sendto.destination";

    private final EventExternalizationConfiguration configuration;

    private final StreamBridge streamBridge;

    private final BindingServiceProperties bindingServiceProperties;

    private final BinderFactory binderFactory;

    private final EvaluationContext context;

    public SpringCloudStreamEventExternalizer(EventExternalizationConfiguration configuration,
            EvaluationContext context, StreamBridge streamBridge, BindingServiceProperties bindingServiceProperties,
            BinderFactory binderFactory) {
        this.configuration = configuration;
        this.context = context;
        this.streamBridge = streamBridge;
        this.bindingServiceProperties = bindingServiceProperties;
        this.binderFactory = binderFactory;
    }

    @Override
    public CompletableFuture<?> apply(RoutingTarget routingTarget, Object event) {
        var routing = BrokerRouting.of(routingTarget, context);

        var target = getTarget(event, routingTarget);
        var keyHeaderValue = routing.getKey(event);
        var keyHeaderName = getKeyHeaderName(target, bindingServiceProperties, binderFactory);

        var headersMap = event instanceof Message ? new LinkedHashMap<>(((Message<?>) event).getHeaders())
                : new LinkedHashMap<String, Object>();
        if (keyHeaderValue != null && keyHeaderName != null) {
            if (!headersMap.containsKey(keyHeaderName)) {
                log.debug("Adding key header to message: {} = {}", keyHeaderName, keyHeaderValue);
                headersMap.put(keyHeaderName, keyHeaderValue);
            }
        }
        var payload = event instanceof Message<?> ? ((Message<?>) event).getPayload() : event;
        var message = MessageBuilder.withPayload(payload).copyHeaders(headersMap).build();

        log.debug("Sending event to Spring Cloud Stream target: {}", target);
        var result = streamBridge.send(target, message);
        return CompletableFuture.completedFuture(result);
    }

    protected static final Map<String, String> messageKeyHeaders = Map.of(
            "org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder", "kafka_messageKey",
            "org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder", "rabbit_routingKey",
            "org.springframework.cloud.stream.binder.kinesis.KinesisMessageChannelBinder", "partitionKey",
            "org.springframework.cloud.stream.binder.pubsub.PubSubMessageChannelBinder", "pubsub_orderingKey",
            "org.springframework.cloud.stream.binder.eventhubs.EventHubsMessageChannelBinder", "partitionKey",
            "org.springframework.cloud.stream.binder.solace.SolaceMessageChannelBinder", "solace_messageKey",
            "org.springframework.cloud.stream.binder.pulsar.PulsarMessageChannelBinder", "pulsar_key");

    protected String getKeyHeaderName(String channelName, BindingServiceProperties bindingServiceProperties,
            BinderFactory binderFactory) {
        String binderConfigurationName = bindingServiceProperties.getBinder(channelName);
        var binder = binderFactory.getBinder(binderConfigurationName, MessageChannel.class);
        if (binder == null) {
            return null;
        }
        return messageKeyHeaders.get(binder.getClass().getName());
    }

    protected String getTarget(Object event, RoutingTarget routingTarget) {
        if (event instanceof Message<?> message) {
            var target = message.getHeaders().get(SPRING_CLOUD_STREAM_SENDTO_DESTINATION_HEADER, String.class);
            return target != null ? target : routingTarget.getTarget();
        }
        return routingTarget.getTarget();
    }

}
