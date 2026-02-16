package org.springframework.modulith.events.scs.dtos.json;

import org.springframework.modulith.events.Externalized;

@Externalized("customers-json-externalized-out-0::#{#this.getName()}")
public class ExternalizedCustomerEvent extends CustomerEvent {

}
