package app.flashcards;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real token decoding: tokens are signed with a local key served as the "Clerk" JWKS. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuthMcpTest {
    static final String MCP_URL = "https://brainrot.example/mcp";
    static final RSAKey KEY = key();
    static final RSAKey OTHER_KEY = key();
    static final HttpServer JWKS = jwks();
    static final String ISSUER = "http://localhost:" + JWKS.getAddress().getPort();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.clerk-issuer", () -> ISSUER);
        r.add("app.mcp-resource-url", () -> MCP_URL);
    }

    @AfterAll
    static void stop() {
        JWKS.stop(0);
    }

    @Autowired
    MockMvc mvc;

    final String alice = "user_" + UUID.randomUUID();

    static final String CALL = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_flashcards",
             "arguments":{"topic":"OAuth","cards":[{"question":"What does PKCE protect?","answer":"The auth code"}]}}}""";

    MockHttpServletRequestBuilder mcp(String token) {
        return post("/mcp").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(CALL);
    }

    String oauthToken(Consumer<JWTClaimsSet.Builder> tweak) {
        return sign(KEY, new JOSEObjectType("at+jwt"), claims -> {
            claims.audience(MCP_URL).claim("client_id", "https://client.example/metadata.json").claim("scope", "profile email");
            tweak.accept(claims);
        });
    }

    String sessionToken(Consumer<JWTClaimsSet.Builder> tweak) {
        return sign(KEY, JOSEObjectType.JWT, claims -> {
            claims.claim("azp", "http://localhost:8088").claim("sid", "sess_1");
            tweak.accept(claims);
        });
    }

    String sign(RSAKey key, JOSEObjectType typ, Consumer<JWTClaimsSet.Builder> tweak) {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(alice)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)));
        tweak.accept(claims);
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(typ).build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void metadataAndChallenge() throws Exception {
        mvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value(MCP_URL))
                .andExpect(jsonPath("$.authorization_servers[0]").value(ISSUER))
                .andExpect(jsonPath("$.authorization_servers.length()").value(1));

        String challenge = "resource_metadata=\"https://brainrot.example/.well-known/oauth-protected-resource/mcp\"";
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(CALL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString(challenge)));
        mvc.perform(mcp(oauthToken(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(600))))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("error=\"invalid_token\"")))
                .andExpect(header().string("WWW-Authenticate", containsString(challenge)));
    }

    @Test
    void oauthTokenUsesMcpAsItsUser() throws Exception {
        String res = mvc.perform(mcp(oauthToken(c -> {}))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assert res.contains("\\\"created\\\":1") : res;
        mvc.perform(get("/api/topics").with(jwt().jwt(j -> j.subject(alice))))
                .andExpect(jsonPath("$[?(@.name=='OAuth')].cardCount").value(1));
    }

    @Test
    void oauthTokenIsMcpOnly() throws Exception {
        String token = oauthToken(c -> {});
        mvc.perform(get("/api/cards/due").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/api-keys").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"sneaky\"}")).andExpect(status().isForbidden());
    }

    @Test
    void badOAuthTokensAreRejected() throws Exception {
        mvc.perform(mcp(oauthToken(c -> c.audience("https://other.example/mcp")))).andExpect(status().isUnauthorized());
        mvc.perform(mcp(oauthToken(c -> c.audience((String) null)))).andExpect(status().isUnauthorized());
        mvc.perform(mcp(oauthToken(c -> c.issuer("https://evil.example")))).andExpect(status().isUnauthorized());
        mvc.perform(mcp(sign(OTHER_KEY, new JOSEObjectType("at+jwt"), c -> c.audience(MCP_URL)))).andExpect(status().isUnauthorized());
        mvc.perform(mcp("not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionTokensStillWork() throws Exception {
        String token = sessionToken(c -> {});
        mvc.perform(get("/api/cards/due").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(mcp(token)).andExpect(status().isOk());
        mvc.perform(get("/api/cards/due").header("Authorization", "Bearer " + sessionToken(c -> c.claim("azp", "https://evil.example"))))
                .andExpect(status().isUnauthorized());
        // a session-typed token doesn't get through by carrying our audience, and an OAuth one isn't a session
        mvc.perform(get("/api/cards/due").header("Authorization", "Bearer "
                + sign(KEY, new JOSEObjectType("jwt+oauth"), c -> c.audience(MCP_URL)))).andExpect(status().isUnauthorized());
    }

    static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static HttpServer jwks() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            s.createContext("/.well-known/jwks.json", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            s.start();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
