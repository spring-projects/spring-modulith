
package org.springframework.modulith.events.scs.dtos.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

public enum PaymentMethodType {

    VISA("VISA"), MASTERCARD("MASTERCARD");

    private final String value;

    private final static Map<String, PaymentMethodType> CONSTANTS = new HashMap<String, PaymentMethodType>();

    static {
        for (PaymentMethodType c : values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    PaymentMethodType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static PaymentMethodType fromValue(String value) {
        PaymentMethodType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        }
        else {
            return constant;
        }
    }

}
