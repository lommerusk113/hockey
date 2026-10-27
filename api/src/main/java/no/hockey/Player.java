package no.hockey;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * @param shortName how the player is written on the results board, e.g. "Ni" for Nico
 */
@Serdeable
@MappedEntity
public record Player(@Id @GeneratedValue @Nullable Long id, @NotBlank String name, @NotBlank String shortName) {
}
