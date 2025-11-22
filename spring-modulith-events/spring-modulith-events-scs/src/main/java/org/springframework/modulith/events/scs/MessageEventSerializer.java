package org.springframework.modulith.events.scs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.core.EventSerializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MessageEventSerializer implements EventSerializer {

    protected Logger log = LoggerFactory.getLogger(getClass());

    private final ObjectMapper jacksonMapper;

    /**
     * Controls whether to store headers types to be used for deserialization. Default is true.
     */
    private boolean processHeaderTypes = true;

    public MessageEventSerializer(ObjectMapper jacksonMapper) {
        this.jacksonMapper = jacksonMapper;
    }

    public void setProcessHeaderTypes(boolean processHeaderTypes) {
        this.processHeaderTypes = processHeaderTypes;
    }

    /**
     * Convert the payload to a Map so it can be serialized to JSON.
     * <p>
     * Subclasses (i.e AvroSerializer) can override this method to customize the serialization.
     *
     * @param payload
     * @return
     */
    protected Map<String, Object> serializeToMap(Object payload) {
        ObjectNode objectNode = jacksonMapper.valueToTree(payload);
        return jacksonMapper.convertValue(objectNode, Map.class);
    }

    @Override
    public Object serialize(Object event) {
        if (event instanceof Message<?> message) {
            Map<String, Object> serializedMessage = new HashMap<>();
            serializedMessage.put("headers", message.getHeaders());
            serializedMessage.put("_header_types", extractHeaderTypes(message.getHeaders()));

            var payload = serializeToMap(message.getPayload());
            payload.put("_class", message.getPayload().getClass().getName());
            serializedMessage.put("payload", payload);

            return jacksonSerialize(serializedMessage);
        }
        return jacksonSerialize(event);
    }

    @Override
    public <T> T deserialize(Object serialized, Class<T> type) {
        try {
            return unsafeDeserialize(serialized, type);
        }
        catch (JsonProcessingException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T unsafeDeserialize(Object serialized, Class<T> type)
            throws JsonProcessingException, ClassNotFoundException {
        if (Message.class.isAssignableFrom(type)) {
            JsonNode node = jacksonMapper.readTree(serialized.toString());
            JsonNode headersNode = node.get("headers");
            Map<String, Object> headers = jacksonMapper.convertValue(headersNode, Map.class);

            if (processHeaderTypes) {
                JsonNode headerTypesNode = node.get("_header_types");
                Map<String, String> headerTypes = jacksonMapper.convertValue(headerTypesNode, Map.class);
                processHeaderTypes(headerTypes, headers);
            }

            JsonNode payloadNode = node.get("payload");
            Object payload = null;
            if (payloadNode.get("_class") != null) {
                Class<?> payloadType = Class.forName(payloadNode.get("_class").asText());
                if (payloadNode instanceof ObjectNode objectNode) {
                    objectNode.remove("_class");
                }
                payload = deserializePayload(payloadNode, payloadType);
            }
            else {
                payload = deserializePayload(payloadNode, Object.class);
            }
            return (T) MessageBuilder.createMessage(payload, new MessageHeaders(headers));
        }
        return jacksonDeserialize(serialized, type);
    }

    protected <T> T deserializePayload(TreeNode payloadNode, Class<T> payloadType) throws JsonProcessingException {
        return jacksonMapper.treeToValue(payloadNode, payloadType);
    }

    protected Object jacksonSerialize(Object event) {
        try {
            var map = serializeToMap(event);
            return jacksonMapper.writeValueAsString(map);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T jacksonDeserialize(Object serialized, Class<T> type) {
        try {
            JsonNode node = jacksonMapper.readTree(serialized.toString());
            return (T) jacksonMapper.readerFor(type).readValue(node);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, String> extractHeaderTypes(Map<String, Object> headers) {
        Map<String, String> headerTypes = new HashMap<>();
        headers.forEach((key, value) -> headerTypes.put(key, value.getClass().getName()));
        return headerTypes;
    }

    protected void processHeaderTypes(Map<String, String> headerTypes, Map<String, Object> headers) {
        if (headerTypes == null) {
            return;
        }
        headers.forEach((key, value) -> {
            if (headerTypes.containsKey(key)) {
                var headerType = headerTypes.get(key);
                try {
                    if (value instanceof String) {
                        headers.put(key, jacksonMapper.convertValue(value, Class.forName(headerType)));
                    }
                    else {
                        headers.put(key, jacksonDeserialize(value, Class.forName(headerType)));
                    }
                }
                catch (Exception e) {
                    log.error("Failed to process header: {key: {}, value: {}, type: {}}. Error: {}", key, value,
                            headerType, e.getMessage());
                }
            }
        });
    }

}
