package app.flashcards;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CardFlowTest {
    @Autowired
    MockMvc mvc;

    final String alice = "user_" + UUID.randomUUID();
    final String bob = "user_" + UUID.randomUUID();

    RequestPostProcessor as(String user) {
        return jwt().jwt(j -> j.subject(user));
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String body(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getContentAsString();
    }

    @Test
    void createDueReviewAndIsolation() throws Exception {
        String res = body(json(post("/api/cards"), """
                {"cards":[{"question":"Capital of France?","answer":"Paris"},
                          {"question":"  capital   of FRANCE? ","answer":"dup"}]}""").with(as(alice)));
        String id = JsonPath.read(res, "$.created[0].id");
        mvc.perform(get("/api/cards/due").with(as(alice)))
                .andExpect(jsonPath("$.dueCount").value(1))
                .andExpect(jsonPath("$.cards[0].id").value(id))
                .andExpect(jsonPath("$.nextDueAt").doesNotExist());
        assert JsonPath.<Integer>read(res, "$.skipped.length()") == 1;

        // another user can't see or touch it
        mvc.perform(patch("/api/cards/" + id).with(as(bob)).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/cards/due").with(as(bob))).andExpect(jsonPath("$.dueCount").value(0));

        mvc.perform(json(post("/api/cards/" + id + "/review"), "{\"quality\":4}").with(as(alice)))
                .andExpect(jsonPath("$.interval").value(1));
        mvc.perform(json(post("/api/cards/" + id + "/review"), "{\"quality\":9}").with(as(alice)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cards/due").with(as(alice)))
                .andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.nextDueAt").exists());

        // search text is literal, not a regex
        mvc.perform(get("/api/cards").param("q", "(a+)+$").with(as(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(0));
        mvc.perform(get("/api/cards").param("q", "OF FRANCE").with(as(alice))).andExpect(jsonPath("$.cards.length()").value(1));
        mvc.perform(get("/api/cards").param("q", "pari").with(as(alice))).andExpect(jsonPath("$.cards.length()").value(1));
    }

    @Test
    void cursorPagination() throws Exception {
        mvc.perform(json(post("/api/cards"), """
                {"cards":[{"question":"q1","answer":"a"},{"question":"q2","answer":"a"},{"question":"q3","answer":"a"}]}""").with(as(alice)));
        String p1 = body(get("/api/cards").param("limit", "2").with(as(alice)));
        assert JsonPath.<String>read(p1, "$.cards[0].question").equals("q3");
        String cursor = JsonPath.read(p1, "$.nextCursor");
        mvc.perform(get("/api/cards").param("limit", "2").param("after", cursor).with(as(alice)))
                .andExpect(jsonPath("$.cards[0].question").value("q1"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void topicsDeleteRulesAndMove() throws Exception {
        String spanish = JsonPath.read(body(json(post("/api/topics"), "{\"name\":\"Spanish\"}").with(as(alice))), "$.id");
        mvc.perform(json(post("/api/topics"), "{\"name\":\"spanish\"}").with(as(alice))).andExpect(status().isConflict());

        mvc.perform(json(post("/api/cards"), "{\"topicId\":\"" + spanish + "\",\"cards\":[{\"question\":\"Hola?\",\"answer\":\"Hello\"}]}").with(as(alice)));
        mvc.perform(json(post("/api/cards"), "{\"cards\":[{\"question\":\"Hola?\",\"answer\":\"Hi\"},{\"question\":\"Adios?\",\"answer\":\"Bye\"}]}").with(as(alice)));

        String def = alice + ":default";
        mvc.perform(delete("/api/topics/" + def).with(as(alice))).andExpect(status().isConflict());
        mvc.perform(delete("/api/topics/" + spanish).with(as(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("1 cards")));

        mvc.perform(json(post("/api/topics/" + def + "/move-cards"), "{\"toTopicId\":\"" + spanish + "\"}").with(as(alice)))
                .andExpect(jsonPath("$.moved").value(1))
                .andExpect(jsonPath("$.skipped[0].question").value("Hola?"));

        mvc.perform(get("/api/topics").with(as(alice)))
                .andExpect(jsonPath("$[?(@.name=='Spanish')].cardCount").value(2))
                .andExpect(jsonPath("$[?(@.name=='Default')].cardCount").value(1));

        mvc.perform(get("/api/cards/due").param("topicId", spanish).with(as(alice))).andExpect(jsonPath("$.dueCount").value(2));
    }

    @Test
    void apiKeysAndMcp() throws Exception {
        String key = JsonPath.read(body(json(post("/api/api-keys"), "{\"name\":\"claude\"}").with(as(alice))), "$.key");
        var auth = "Bearer " + key;

        mvc.perform(get("/api/cards/due").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/cards/due").header("Authorization", "Bearer fc_nope")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cards/due")).andExpect(status().isUnauthorized());
        // a key can't mint keys
        mvc.perform(json(post("/api/api-keys"), "{\"name\":\"x\"}").header("Authorization", auth)).andExpect(status().isForbidden());

        String call = """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_flashcards",
                 "arguments":{"topic":"Go","cards":[{"question":"Zero value of int?","answer":"0"}]}}}""";
        String res = body(post("/mcp").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(call));
        assert res.contains("\\\"created\\\":1") : res;

        mvc.perform(get("/api/topics").with(as(alice)))
                .andExpect(jsonPath("$[?(@.name=='Go')].cardCount").value(1));
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(call)).andExpect(status().isUnauthorized());
    }
}
