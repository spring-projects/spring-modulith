package org.springframework.modulith.events.scs.dtos.avro;

import org.springframework.modulith.events.Externalized;

@Externalized("customers-avro-externalized-out-0::#{#this.getName()}")
public class ExternalizedCustomerEvent extends CustomerEvent {

}
