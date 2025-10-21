
package org.springframework.modulith.events.scs.dtos.json;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "type", "cardNumber" })
public class PaymentMethod implements Serializable {

    @JsonProperty("id")
    private Long id;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    @NotNull
    private PaymentMethodType type;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("cardNumber")
    @NotNull
    private String cardNumber;

    @JsonIgnore
    @Valid
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    protected final static Object NOT_FOUND_VALUE = new Object();

    private final static long serialVersionUID = -8629217294484723648L;

    @JsonProperty("id")
    public Long getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Long id) {
        this.id = id;
    }

    public PaymentMethod withId(Long id) {
        this.id = id;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    public PaymentMethodType getType() {
        return type;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("type")
    public void setType(PaymentMethodType type) {
        this.type = type;
    }

    public PaymentMethod withType(PaymentMethodType type) {
        this.type = type;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("cardNumber")
    public String getCardNumber() {
        return cardNumber;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("cardNumber")
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public PaymentMethod withCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public PaymentMethod withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    protected boolean declaredProperty(String name, Object value) {
        if ("id".equals(name)) {
            if (value instanceof Long) {
                setId(((Long) value));
            }
            else {
                throw new IllegalArgumentException(
                        ("property \"id\" is of type \"java.lang.Long\", but got " + value.getClass().toString()));
            }
            return true;
        }
        else {
            if ("type".equals(name)) {
                if (value instanceof PaymentMethodType) {
                    setType(((PaymentMethodType) value));
                }
                else {
                    throw new IllegalArgumentException(
                            ("property \"type\" is of type \"io.zenwave360.example.core.outbound.events.dtos.PaymentMethodType\", but got "
                                    + value.getClass().toString()));
                }
                return true;
            }
            else {
                if ("cardNumber".equals(name)) {
                    if (value instanceof String) {
                        setCardNumber(((String) value));
                    }
                    else {
                        throw new IllegalArgumentException(
                                ("property \"cardNumber\" is of type \"java.lang.String\", but got "
                                        + value.getClass().toString()));
                    }
                    return true;
                }
                else {
                    return false;
                }
            }
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        if ("id".equals(name)) {
            return getId();
        }
        else {
            if ("type".equals(name)) {
                return getType();
            }
            else {
                if ("cardNumber".equals(name)) {
                    return getCardNumber();
                }
                else {
                    return notFoundValue;
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked" })
    public <T> T get(String name) {
        Object value = declaredPropertyOrNotFound(name, PaymentMethod.NOT_FOUND_VALUE);
        if (PaymentMethod.NOT_FOUND_VALUE != value) {
            return ((T) value);
        }
        else {
            return ((T) getAdditionalProperties().get(name));
        }
    }

    public void set(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, ((Object) value));
        }
    }

    public PaymentMethod with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, ((Object) value));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(PaymentMethod.class.getName())
            .append('@')
            .append(Integer.toHexString(System.identityHashCode(this)))
            .append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("cardNumber");
        sb.append('=');
        sb.append(((this.cardNumber == null) ? "<null>" : this.cardNumber));
        sb.append(',');
        sb.append("additionalProperties");
        sb.append('=');
        sb.append(((this.additionalProperties == null) ? "<null>" : this.additionalProperties));
        sb.append(',');
        if (sb.charAt((sb.length() - 1)) == ',') {
            sb.setCharAt((sb.length() - 1), ']');
        }
        else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.additionalProperties == null) ? 0 : this.additionalProperties.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.cardNumber == null) ? 0 : this.cardNumber.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PaymentMethod) == false) {
            return false;
        }
        PaymentMethod rhs = ((PaymentMethod) other);
        return (((((this.id == rhs.id) || ((this.id != null) && this.id.equals(rhs.id)))
                && ((this.additionalProperties == rhs.additionalProperties) || ((this.additionalProperties != null)
                        && this.additionalProperties.equals(rhs.additionalProperties))))
                && ((this.type == rhs.type) || ((this.type != null) && this.type.equals(rhs.type))))
                && ((this.cardNumber == rhs.cardNumber)
                        || ((this.cardNumber != null) && this.cardNumber.equals(rhs.cardNumber))));
    }

}
