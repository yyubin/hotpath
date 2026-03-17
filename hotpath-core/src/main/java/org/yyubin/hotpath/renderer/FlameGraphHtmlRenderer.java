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

    private static final String PLACEHOLDER = "/*__HOTPATH_DATA__*/";

    private final ObjectMapper mapper;

    public FlameGraphHtmlRenderer() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void render(FlameGraphResult result, Path outputPath) throws IOException {
        String template = loadTemplate();
        String json     = mapper.writeValueAsString(result);
        String html     = template.replace(PLACEHOLDER, json);
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/flamegraph-report.html")) {
            if (is == null) throw new IOException("템플릿 파일을 찾을 수 없습니다: /templates/flamegraph-report.html");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
