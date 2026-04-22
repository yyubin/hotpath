package org.yyubin.hotpath.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.yyubin.hotpath.model.flamegraph.FlameGraphResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlameGraphHtmlRenderer {

    private static final String DATA_PLACEHOLDER = "/*__HOTPATH_DATA__*/";
    private static final String LANG_PLACEHOLDER = "{{lang}}";

    private final ObjectMapper mapper;
    private final String       langTag;

    public FlameGraphHtmlRenderer(String langTag) {
        this.langTag = langTag;
        this.mapper  = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void render(FlameGraphResult result, Path outputPath) throws IOException {
        String template = loadTemplate();
        String json     = mapper.writeValueAsString(result);
        String html     = template
                .replace(LANG_PLACEHOLDER, langTag)
                .replace(DATA_PLACEHOLDER, json);
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/flamegraph-report.html")) {
            if (is == null) throw new IOException("Template not found: /templates/flamegraph-report.html");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
