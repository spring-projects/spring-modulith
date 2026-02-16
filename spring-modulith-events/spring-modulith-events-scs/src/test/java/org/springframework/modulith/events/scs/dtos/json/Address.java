
package org.springframework.modulith.events.scs.dtos.json;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "street", "city" })
public class Address implements Serializable {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("street")
    @Size(max = 254)
    @NotNull
    private String street;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("city")
    @Size(max = 254)
    @NotNull
    private String city;

    @JsonIgnore
    @Valid
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    protected final static Object NOT_FOUND_VALUE = new Object();

    private final static long serialVersionUID = -2054487487628445756L;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("street")
    public String getStreet() {
        return street;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("street")
    public void setStreet(String street) {
        this.street = street;
    }

    public Address withStreet(String street) {
        this.street = street;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("city")
    public String getCity() {
        return city;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("city")
    public void setCity(String city) {
        this.city = city;
    }

    public Address withCity(String city) {
        this.city = city;
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

    public Address withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    protected boolean declaredProperty(String name, Object value) {
        if ("street".equals(name)) {
            if (value instanceof String) {
                setStreet(((String) value));
            }
            else {
                throw new IllegalArgumentException(("property \"street\" is of type \"java.lang.String\", but got "
                        + value.getClass().toString()));
            }
            return true;
        }
        else {
            if ("city".equals(name)) {
                if (value instanceof String) {
                    setCity(((String) value));
                }
                else {
                    throw new IllegalArgumentException(("property \"city\" is of type \"java.lang.String\", but got "
                            + value.getClass().toString()));
                }
                return true;
            }
            else {
                return false;
            }
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        if ("street".equals(name)) {
            return getStreet();
        }
        else {
            if ("city".equals(name)) {
                return getCity();
            }
            else {
                return notFoundValue;
            }
        }
    }

    @SuppressWarnings({ "unchecked" })
    public <T> T get(String name) {
        Object value = declaredPropertyOrNotFound(name, Address.NOT_FOUND_VALUE);
        if (Address.NOT_FOUND_VALUE != value) {
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

    public Address with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, ((Object) value));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Address.class.getName())
            .append('@')
            .append(Integer.toHexString(System.identityHashCode(this)))
            .append('[');
        sb.append("street");
        sb.append('=');
        sb.append(((this.street == null) ? "<null>" : this.street));
        sb.append(',');
        sb.append("city");
        sb.append('=');
        sb.append(((this.city == null) ? "<null>" : this.city));
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
        result = ((result * 31) + ((this.additionalProperties == null) ? 0 : this.additionalProperties.hashCode()));
        result = ((result * 31) + ((this.city == null) ? 0 : this.city.hashCode()));
        result = ((result * 31) + ((this.street == null) ? 0 : this.street.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Address) == false) {
            return false;
        }
        Address rhs = ((Address) other);
        return ((((this.additionalProperties == rhs.additionalProperties)
                || ((this.additionalProperties != null) && this.additionalProperties.equals(rhs.additionalProperties)))
                && ((this.city == rhs.city) || ((this.city != null) && this.city.equals(rhs.city))))
                && ((this.street == rhs.street) || ((this.street != null) && this.street.equals(rhs.street))));
    }

}
