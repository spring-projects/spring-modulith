package org.springframework.modulith.maven.sample.order;

import org.springframework.modulith.maven.sample.inventory.InventoryManagement;
import org.springframework.stereotype.Service;

@Service
public class OrderManagement {

	private final InventoryManagement inventory;

	OrderManagement(InventoryManagement inventory) {
		this.inventory = inventory;
	}

	public String create(String item) {
		return inventory.reserve(item);
	}
}

