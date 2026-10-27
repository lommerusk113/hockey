package no.hockey;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
@MappedEntity
public record ChatMessage(@Id @GeneratedValue @Nullable Long id,
                          Long userId,
                          String body,
                          @DateCreated @Nullable Instant createdAt) {
}
