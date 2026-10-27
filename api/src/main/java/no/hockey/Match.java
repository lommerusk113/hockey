package no.hockey;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Serdeable
@MappedEntity("match")
public record Match(@Id @GeneratedValue @Nullable Long id,
                    @Nullable Instant playedAt,
                    @NotNull Long homePlayerId,
                    @NotNull Long awayPlayerId,
                    @Min(0) int homeGoals,
                    @Min(0) int awayGoals) {
}
