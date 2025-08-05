# Spring-Modulith Events Externalizer for Spring Cloud Stream

[![Maven Central](https://img.shields.io/maven-central/v/io.zenwave360.sdk/spring-modulith-events-scs.svg?label=Maven%20Central&logo=apachemaven)](https://search.maven.org/artifact/io.zenwave360.sdk/spring-modulith-events-scs)
[![build](https://github.com/ZenWave360/spring-modulith-events-spring-cloud-stream/workflows/Build/badge.svg)](https://github.com/ZenWave360/spring-modulith-events-spring-cloud-stream/actions/workflows/build.yml)
[![coverage](https://raw.githubusercontent.com/ZenWave360/spring-modulith-events-spring-cloud-stream/badges/jacoco.svg)](https://github.com/ZenWave360/spring-modulith-events-spring-cloud-stream/actions/workflows/build.yml)
[![branches coverage](https://raw.githubusercontent.com/ZenWave360/spring-modulith-events-spring-cloud-stream/badges/branches.svg)](https://github.com/ZenWave360/spring-modulith-events-spring-cloud-stream/actions/workflows/build.yml)
[![GitHub](https://img.shields.io/github/license/ZenWave360/spring-modulith-events-spring-cloud-stream)](https://github.com/ZenWave360/spring-modulith-events-spring-cloud-stream/blob/main/LICENSE)

Spring-Modulith Events Externalizer that uses Spring Cloud Stream supporting both JSON and Avro serialization formats.

## Getting Started

### Dependency
Add the following Maven dependency to your project:

```xml
<dependency>
    <groupId>io.zenwave360.sdk</groupId>
    <artifactId>spring-modulith-events-scs</artifactId>
    <version>${spring-modulith-events-scs.version}</version>
</dependency>
```

### Configuration
Use `@EnableSpringCloudStreamEventExternalization` annotation to enable Spring Cloud Stream event externalization in your Spring configuration:

```java
@Configuration
@EnableSpringCloudStreamEventExternalization
public class SpringCloudStreamEventsConfig {
    // Additional configurations (if needed)
}
```

This configuration ensures that, in addition to events annotated with `@Externalized`, all events of type `org.springframework.messaging.Message` with a header named `SpringCloudStreamEventExternalizer.SPRING_CLOUD_STREAM_EVENT_HEADER` will be externalized and routed to their specified destination using the value of this header as the routing target.

---

## Event Serialization

Using the transactional event publication log requires serializing events to a format that can be stored in a database. Since the generic type of `Message<?>` payload is lost when using the default `JacksonEventSerializer`, this library adds an extra `_class` field to preserve payload type information, allowing for complete deserialization to its original type.

This library provides support for POJO (JSON) and Avro serialization formats for `Message<?>` payloads.

### Avro Serialization

Avro serialization needs `com.fasterxml.jackson.dataformat.avro.AvroMapper` class present in the classpath. In order to use Avro serialization, you need to add the following dependency to your project:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-avro</artifactId>
</dependency>
```

---

## Routing Events

### Programmatic Routing for `Message<?`> events

You can define routing targets programmatically using a Message header:

```java
public class CustomerEventsProducer implements ICustomerEventsProducer {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void onCustomerCreated(CustomerCreated event) {
        Message<CustomerCreated> message = MessageBuilder.withPayload(event)
                .setHeader(
                        SpringCloudStreamEventExternalizer.SPRING_CLOUD_STREAM_SENDTO_DESTINATION_HEADER, 
                        "customer-created") // <- target binding name
                .build();
        applicationEventPublisher.publishEvent(message);
    }
}
```

### Annotation-Based Routing for POJO Events

Leverage the `@Externalized` annotation to define the target binding name and routing key:

```java
@Externalized("customer-created::#{#this.getLastname()}")
class CustomerCreated {

    public String getLastname() {
        // Return the customer's last name
    }
}
```

### Configure Spring Cloud Stream destination

Configure Spring Cloud Stream destination for your bindings as usual in `application.yml`:

```yaml
spring:
  cloud:
    stream:
      bindings:
        customer-created:
          destination: customer-created-topic
```

### Routing Key

`SpringCloudStreamEventExternalizer` dynamically sets the appropriate Message header (e.g., `kafka_messageKey` or `rabbit_routingKey`) from your routing key based on the channel binder type, if the routing header is not already present.

- KafkaMessageChannelBinder: `kafka_messageKey`
- RabbitMessageChannelBinder: `rabbit_routingKey`
- KinesisMessageChannelBinder: `partitionKey`
- PubSubMessageChannelBinder: `pubsub_orderingKey`
- EventHubsMessageChannelBinder: `partitionKey`
- SolaceMessageChannelBinder: `solace_messageKey`
- PulsarMessageChannelBinder: `pulsar_key`

---

## Using Snapshot Versions
In order to test snapshot versions of this library, add the following repository to your Maven configuration:

```xml
<repository>
    <id>gh</id>
    <url>https://raw.githubusercontent.com/ZenWave360/maven-snapshots/refs/heads/main</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```
