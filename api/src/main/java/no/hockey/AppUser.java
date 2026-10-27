package no.hockey;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Someone who signed in with Google, optionally linked to the player they are on the board.
 */
@Serdeable
@MappedEntity
public record AppUser(@Id @GeneratedValue @Nullable Long id,
                      String googleSubject,
                      String email,
                      String name,
                      @Nullable String pictureUrl,
                      @Nullable Long playerId,
                      boolean notifyMatches,
                      boolean notifyChat) {

    public AppUser withSettings(@Nullable Long playerId, boolean notifyMatches, boolean notifyChat) {
        return new AppUser(id, googleSubject, email, name, pictureUrl, playerId, notifyMatches, notifyChat);
    }

    public AppUser withProfile(String email, String name, @Nullable String pictureUrl) {
        return new AppUser(id, googleSubject, email, name, pictureUrl, playerId, notifyMatches, notifyChat);
    }
}
