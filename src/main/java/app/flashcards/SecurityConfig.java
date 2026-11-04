package app.flashcards;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Two ways in: a Clerk session JWT (web UI) or an "fc_" API key (agents). Both end up with the Clerk user id
 * as the authentication name, so {@link #userId()} works for REST and MCP alike.
 */
@Configuration
class SecurityConfig {

    static String userId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, MongoTemplate mongo) throws Exception {
        var defaultResolver = new DefaultBearerTokenResolver();
        return http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Data needs a login; everything else is the web app's static files (public)
                        .requestMatchers("/api/**", "/mcp", "/mcp/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new ApiKeyFilter(mongo), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(o -> o
                        // API keys are handled by ApiKeyFilter; keep the JWT decoder away from them
                        .bearerTokenResolver(req -> {
                            String t = defaultResolver.resolve(req);
                            return t != null && t.startsWith(ApiKeyController.PREFIX) ? null : t;
                        })
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.clerk-issuer}") String issuer, @Value("${app.allowed-origins}") List<String> origins) {
        // JWKS is fetched lazily on first token, so startup doesn't need Clerk to be reachable
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> { // Clerk: azp must be one of our frontends (absent on backend-minted tokens)
                    String azp = jwt.getClaimAsString("azp");
                    return azp == null || origins.contains(azp)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "azp not allowed", null));
                }));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.allowed-origins}") List<String> origins) {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    /** Authenticates "Authorization: Bearer fc_..." against the api_keys collection. Invalid keys fall through to 401. */
    static class ApiKeyFilter extends OncePerRequestFilter {
        private final MongoTemplate mongo;

        ApiKeyFilter(MongoTemplate mongo) {
            this.mongo = mongo;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer " + ApiKeyController.PREFIX)) {
                ApiKey key = mongo.findAndModify(query(where("keyHash").is(ApiKeyController.hash(h.substring(7)))),
                        new Update().set("lastUsedAt", Instant.now()), ApiKey.class);
                if (key != null) {
                    var ctx = SecurityContextHolder.createEmptyContext();
                    ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(key.getUserId(), null, List.of()));
                    SecurityContextHolder.setContext(ctx);
                }
            }
            chain.doFilter(req, res);
        }
    }
}
