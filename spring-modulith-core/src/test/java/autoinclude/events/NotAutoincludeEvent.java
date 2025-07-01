package autoinclude.events;

import org.springframework.modulith.NamedInterface;

@NamedInterface(name = "events.NotAutoincludeEvent", autoIncludeRelatedTypes = false)
public record NotAutoincludeEvent(RelatedType relatedType, AnEnum anEnum) {
}
