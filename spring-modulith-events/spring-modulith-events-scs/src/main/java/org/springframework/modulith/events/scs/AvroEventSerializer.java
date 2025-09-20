package org.springframework.modulith.events.scs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import org.springframework.modulith.events.core.EventSerializer;

import java.util.Map;

public class AvroEventSerializer extends MessageEventSerializer implements EventSerializer {

    private AvroMapper avroMapper;

    public AvroEventSerializer(ObjectMapper jacksonMapper) {
        super(jacksonMapper);
        this.avroMapper = AvroMapper.builder().build();
    }

    public AvroEventSerializer(AvroMapper avroMapper, ObjectMapper jacksonMapper) {
        super(jacksonMapper);
        this.avroMapper = avroMapper;
    }

    protected Map<String, Object> serializeToMap(Object payload) {
        ObjectNode objectNode = avroMapper.valueToTree(payload);
        objectNode.remove("specificData"); // TODO: remove this recursively
        return avroMapper.convertValue(objectNode, Map.class);
    }

}
