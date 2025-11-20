package org.springframework.modulith.events;

import java.time.Instant;

public interface FailedAttemptInfo {
    /**
     * Returns the time the event is published at.
     *
     * @return will never be {@literal null}.
     */
    Instant getPublicationDate();

    /**
     * Returns the exception causing the publication to fail
     *
     * @return will never be {@literal null}.
     */
    Throwable getFailureReason();
}
