package no.hockey;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import no.hockey.SocialRepositories.AppUserRepository;

/**
 * Local testing without Google: POST /login with any username signs in as that user. Never enable in a deployment.
 */
@Singleton
@Requires(property = "auth.dev-login", value = "true")
public class DevAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

    private final AppUserRepository users;

    public DevAuthenticationProvider(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public AuthenticationResponse authenticate(@Nullable HttpRequest<B> request, AuthenticationRequest<String, String> authRequest) {

        var name = authRequest.getIdentity();
        var user = users.findByGoogleSubject("dev:" + name)
            .orElseGet(() -> users.save(new AppUser(null, "dev:" + name, name + "@dev.local", name, null, null, true, true)));

        return AuthenticationResponse.success(String.valueOf(user.id()));
    }
}
