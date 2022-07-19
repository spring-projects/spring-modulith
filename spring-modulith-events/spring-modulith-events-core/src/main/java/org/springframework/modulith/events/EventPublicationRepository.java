package org.springframework.modulith.events;

import java.util.List;
import java.util.Optional;

/**
 * Repository to store {@link EventPublication}s.
 *
 * @author Björn Kieling, Dmitry Belyaev
 */
public interface EventPublicationRepository {

    EventPublication create(EventPublication publication);

    EventPublication updateCompletionDate(CompletableEventPublication publication);

    /**
     * Returns all {@link EventPublication} that have not been completed yet.
     */
    List<EventPublication> findByCompletionDateIsNull();

    /**
     * Return the {@link EventPublication} for the given serialized event and listener identifier.
     *
     * @param event            must not be {@literal null}.
     * @param targetIdentifier must not be {@literal null}.
     * @return
     */
    Optional<EventPublication> findByEventAndTargetIdentifier(Object event, PublicationTargetIdentifier targetIdentifier);
}
