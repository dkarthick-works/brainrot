package app.flashcards;

import com.nimbusds.jwt.JWTParser;
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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Three ways in, all ending with the Clerk user id as the authentication name, so {@link #userId()} works everywhere:
 * <ul>
 *   <li>a Clerk session JWT (web UI): REST and MCP;</li>
 *   <li>an "fc_" API key (scripts, agents): REST and MCP;</li>
 *   <li>a Clerk OAuth access token (MCP clients that "Sign in with Clerk"): MCP only. Clerk marks these with the
 *       {@code at+jwt} header type and sets {@code aud} to our MCP URL. They never count as a signed-in session,
 *       so they can't reach the REST API or mint API keys.</li>
 * </ul>
 * MCP clients discover the OAuth setup from {@code /.well-known/oauth-protected-resource/mcp} (RFC 9728), which the
 * 401 challenge points at.
 */
@Configuration
class SecurityConfig {
    /** Authority held only by OAuth access tokens; see the class comment. */
    static final String MCP_OAUTH = "MCP_OAUTH";
    private static final List<String> OAUTH_TYPES = List.of("at+jwt", "application/at+jwt");

    static String userId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, MongoTemplate mongo, @Value("${app.clerk-issuer}") String issuer,
                              @Value("${app.mcp-resource-url}") String mcpUrl) throws Exception {
        var defaultResolver = new DefaultBearerTokenResolver();
        // RFC 9728: metadata for resource https://host/mcp lives at https://host/.well-known/oauth-protected-resource/mcp
        var mcp = UriComponentsBuilder.fromUriString(mcpUrl).build();
        String metadataUrl = UriComponentsBuilder.fromUriString(mcpUrl)
                .replacePath("/.well-known/oauth-protected-resource" + mcp.getPath()).build().toUriString();
        var entryPoint = new BearerTokenAuthenticationEntryPoint();
        entryPoint.setResourceMetadataParameterResolver(req -> metadataUrl);
        return http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Data needs a login; everything else is the web app's static files (public)
                        .requestMatchers("/api/**").access(AuthorizationManagers.allOf(
                                AuthenticatedAuthorizationManager.authenticated(),
                                AuthorizationManagers.not(AuthorityAuthorizationManager.hasAuthority(MCP_OAUTH))))
                        .requestMatchers("/mcp", "/mcp/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new ApiKeyFilter(mongo), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(o -> o
                        // API keys are handled by ApiKeyFilter; keep the JWT decoder away from them
                        .bearerTokenResolver(req -> {
                            String t = defaultResolver.resolve(req);
                            return t != null && t.startsWith(ApiKeyController.PREFIX) ? null : t;
                        })
                        .jwt(j -> j.jwtAuthenticationConverter(SecurityConfig::toAuthentication))
                        .authenticationEntryPoint(entryPoint)
                        // Configured URLs, not request headers: behind the proxy the request URL is http://internal
                        .protectedResourceMetadata(m -> m.protectedResourceMetadataCustomizer(b -> b
                                .resource(mcpUrl)
                                .authorizationServers(list -> { list.clear(); list.add(issuer); })
                                .scopes(list -> { list.clear(); list.addAll(List.of("profile", "email")); })
                                .resourceName("brainrot flashcards"))))
                .build();
    }

    /** Session tokens keep the usual JwtAuthenticationToken; OAuth tokens get a separate type and the MCP_OAUTH authority. */
    private static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
        return isOAuth(jwt.getHeaders().get("typ"))
                ? UsernamePasswordAuthenticationToken.authenticated(jwt.getSubject(), jwt, List.of(new SimpleGrantedAuthority(MCP_OAUTH)))
                : new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }

    private static boolean isOAuth(Object typ) {
        return typ instanceof String t && OAUTH_TYPES.contains(t.toLowerCase());
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.clerk-issuer}") String issuer, @Value("${app.allowed-origins}") List<String> origins,
                          @Value("${app.mcp-resource-url}") String mcpUrl) {
        // Both token kinds are signed with the instance's keys. JWKS is fetched lazily on first token,
        // so startup doesn't need Clerk to be reachable
        String jwks = issuer + "/.well-known/jwks.json";

        // Session tokens: the default validators also reject any typ other than JWT, so at+jwt can't end up here
        NimbusJwtDecoder session = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        session.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> { // Clerk: azp must be one of our frontends (absent on backend-minted tokens)
                    String azp = jwt.getClaimAsString("azp");
                    return azp == null || origins.contains(azp)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "azp not allowed", null));
                }));

        // OAuth access tokens: must be issued for our MCP endpoint (Clerk sets aud once the MCP server is added)
        NimbusJwtDecoder oauth = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        oauth.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new JwtTypeValidator(OAUTH_TYPES),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(mcpUrl)),
                new JwtClaimValidator<String>("sub", sub -> sub != null && !sub.isBlank()))));

        return token -> {
            Object typ;
            try {
                typ = JWTParser.parse(token).getHeader().getType();
            } catch (ParseException e) {
                throw new BadJwtException("Malformed token", e);
            }
            return isOAuth(typ == null ? null : typ.toString()) ? oauth.decode(token) : session.decode(token);
        };
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
