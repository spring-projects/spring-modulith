
package org.springframework.modulith.events.scs.dtos.json;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "email", "addresses", "id", "version", "paymentMethods" })
public class CustomerEvent implements Serializable {

    /**
     * Customer name (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Customer name")
    @Size(max = 254)
    @NotNull
    private String name;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("email")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}")
    @Size(max = 254)
    @NotNull
    private String email;

    @JsonProperty("addresses")
    @Valid
    private List<Address> addresses = new ArrayList<Address>();

    @JsonProperty("id")
    private Long id;

    @JsonProperty("version")
    private Long version;

    @JsonProperty("paymentMethods")
    @Valid
    private List<PaymentMethod> paymentMethods = new ArrayList<PaymentMethod>();

    @JsonIgnore
    @Valid
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    protected final static Object NOT_FOUND_VALUE = new Object();

    private final static long serialVersionUID = 2415813790372213850L;

    /**
     * Customer name (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Customer name (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public CustomerEvent withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("email")
    public void setEmail(String email) {
        this.email = email;
    }

    public CustomerEvent withEmail(String email) {
        this.email = email;
        return this;
    }

    @JsonProperty("addresses")
    public List<Address> getAddresses() {
        return addresses;
    }

    @JsonProperty("addresses")
    public void setAddresses(List<Address> addresses) {
        this.addresses = addresses;
    }

    public CustomerEvent withAddresses(List<Address> addresses) {
        this.addresses = addresses;
        return this;
    }

    @JsonProperty("id")
    public Long getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Long id) {
        this.id = id;
    }

    public CustomerEvent withId(Long id) {
        this.id = id;
        return this;
    }

    @JsonProperty("version")
    public Long getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Long version) {
        this.version = version;
    }

    public CustomerEvent withVersion(Long version) {
        this.version = version;
        return this;
    }

    @JsonProperty("paymentMethods")
    public List<PaymentMethod> getPaymentMethods() {
        return paymentMethods;
    }

    @JsonProperty("paymentMethods")
    public void setPaymentMethods(List<PaymentMethod> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    public CustomerEvent withPaymentMethods(List<PaymentMethod> paymentMethods) {
        this.paymentMethods = paymentMethods;
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

    public CustomerEvent withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    protected boolean declaredProperty(String name, Object value) {
        if ("name".equals(name)) {
            if (value instanceof String) {
                setName(((String) value));
            }
            else {
                throw new IllegalArgumentException(
                        ("property \"name\" is of type \"java.lang.String\", but got " + value.getClass().toString()));
            }
            return true;
        }
        else {
            if ("email".equals(name)) {
                if (value instanceof String) {
                    setEmail(((String) value));
                }
                else {
                    throw new IllegalArgumentException(("property \"email\" is of type \"java.lang.String\", but got "
                            + value.getClass().toString()));
                }
                return true;
            }
            else {
                if ("addresses".equals(name)) {
                    if (value instanceof List) {
                        setAddresses(((List<Address>) value));
                    }
                    else {
                        throw new IllegalArgumentException(
                                ("property \"addresses\" is of type \"java.util.List<io.zenwave360.example.core.outbound.events.dtos.Address>\", but got "
                                        + value.getClass().toString()));
                    }
                    return true;
                }
                else {
                    if ("id".equals(name)) {
                        if (value instanceof Long) {
                            setId(((Long) value));
                        }
                        else {
                            throw new IllegalArgumentException(
                                    ("property \"id\" is of type \"java.lang.Long\", but got "
                                            + value.getClass().toString()));
                        }
                        return true;
                    }
                    else {
                        if ("version".equals(name)) {
                            if (value instanceof Long) {
                                setVersion(((Long) value));
                            }
                            else {
                                throw new IllegalArgumentException(
                                        ("property \"version\" is of type \"java.lang.Long\", but got "
                                                + value.getClass().toString()));
                            }
                            return true;
                        }
                        else {
                            if ("paymentMethods".equals(name)) {
                                if (value instanceof List) {
                                    setPaymentMethods(((List<PaymentMethod>) value));
                                }
                                else {
                                    throw new IllegalArgumentException(
                                            ("property \"paymentMethods\" is of type \"java.util.List<io.zenwave360.example.core.outbound.events.dtos.PaymentMethod>\", but got "
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
            }
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        if ("name".equals(name)) {
            return getName();
        }
        else {
            if ("email".equals(name)) {
                return getEmail();
            }
            else {
                if ("addresses".equals(name)) {
                    return getAddresses();
                }
                else {
                    if ("id".equals(name)) {
                        return getId();
                    }
                    else {
                        if ("version".equals(name)) {
                            return getVersion();
                        }
                        else {
                            if ("paymentMethods".equals(name)) {
                                return getPaymentMethods();
                            }
                            else {
                                return notFoundValue;
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked" })
    public <T> T get(String name) {
        Object value = declaredPropertyOrNotFound(name, CustomerEvent.NOT_FOUND_VALUE);
        if (CustomerEvent.NOT_FOUND_VALUE != value) {
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

    public CustomerEvent with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, ((Object) value));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(CustomerEvent.class.getName())
            .append('@')
            .append(Integer.toHexString(System.identityHashCode(this)))
            .append('[');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null) ? "<null>" : this.name));
        sb.append(',');
        sb.append("email");
        sb.append('=');
        sb.append(((this.email == null) ? "<null>" : this.email));
        sb.append(',');
        sb.append("addresses");
        sb.append('=');
        sb.append(((this.addresses == null) ? "<null>" : this.addresses));
        sb.append(',');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("version");
        sb.append('=');
        sb.append(((this.version == null) ? "<null>" : this.version));
        sb.append(',');
        sb.append("paymentMethods");
        sb.append('=');
        sb.append(((this.paymentMethods == null) ? "<null>" : this.paymentMethods));
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
        result = ((result * 31) + ((this.addresses == null) ? 0 : this.addresses.hashCode()));
        result = ((result * 31) + ((this.paymentMethods == null) ? 0 : this.paymentMethods.hashCode()));
        result = ((result * 31) + ((this.name == null) ? 0 : this.name.hashCode()));
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.additionalProperties == null) ? 0 : this.additionalProperties.hashCode()));
        result = ((result * 31) + ((this.version == null) ? 0 : this.version.hashCode()));
        result = ((result * 31) + ((this.email == null) ? 0 : this.email.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof CustomerEvent) == false) {
            return false;
        }
        CustomerEvent rhs = ((CustomerEvent) other);
        return ((((((((this.addresses == rhs.addresses)
                || ((this.addresses != null) && this.addresses.equals(rhs.addresses)))
                && ((this.paymentMethods == rhs.paymentMethods)
                        || ((this.paymentMethods != null) && this.paymentMethods.equals(rhs.paymentMethods))))
                && ((this.name == rhs.name) || ((this.name != null) && this.name.equals(rhs.name))))
                && ((this.id == rhs.id) || ((this.id != null) && this.id.equals(rhs.id))))
                && ((this.additionalProperties == rhs.additionalProperties) || ((this.additionalProperties != null)
                        && this.additionalProperties.equals(rhs.additionalProperties))))
                && ((this.version == rhs.version) || ((this.version != null) && this.version.equals(rhs.version))))
                && ((this.email == rhs.email) || ((this.email != null) && this.email.equals(rhs.email))));
    }

}
