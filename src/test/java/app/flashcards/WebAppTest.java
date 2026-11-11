package app.flashcards;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebAppTest {
    @Test
    void directClientRoutesAndRefreshForwardToApp() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new WebApp()).build();
        for (var route : new String[]{"/freestyle", "/topics", "/cards", "/settings", "/settings/agents"}) {
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
            mvc.perform(get(route).header("Cache-Control", "no-cache"))
                    .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/api/not-an-endpoint")).andExpect(status().isNotFound());
    }
}
