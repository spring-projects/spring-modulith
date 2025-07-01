package autoinclude.events;

import org.springframework.modulith.NamedInterface;

@NamedInterface(name = "events.AnAutoincludeEvent", autoIncludeRelatedTypes = true)
public record AnAutoincludeEvent(RelatedType relatedType, AnEnum anEnum) {
}
