package org.springframework.modulith.events.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.integration.support.locks.LockRegistry;

/**
 * @author Josh Long
 */
@AutoConfiguration 
@ConditionalOnClass (LockRegistry.class)
class IntegrationLockConfiguration {
    
    
    
}
