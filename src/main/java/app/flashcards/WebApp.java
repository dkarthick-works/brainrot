package app.flashcards;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/** Serves the frontend build (copied into classpath:/static by the Dockerfile) from the same origin as the API. */
@Configuration
@Controller
class WebApp implements WebMvcConfigurer {

    // Client-side routes (frontend App.tsx): a reload or deep link gets the app, which then routes itself
    @GetMapping({"/topics", "/cards", "/settings", "/settings/agents"})
    String app() {
        return "forward:/index.html";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry r) {
        // Hashed file names never change: cache forever
        r.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // index.html, sw.js, manifest: always re-checked, or a deploy never reaches installed apps
        r.addResourceHandler("/**").addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }
}
