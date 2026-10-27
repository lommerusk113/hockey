package no.hockey;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@MappedEntity
public record ChatReaction(@Id @GeneratedValue @Nullable Long id, Long messageId, Long userId, String emoji) {
}
