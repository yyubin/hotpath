package org.yyubin.hotpath.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.yyubin.hotpath.model.AnalysisResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HtmlRenderer {

    private static final String PLACEHOLDER = "/*__HOTPATH_DATA__*/";

    private final ObjectMapper mapper;

    public HtmlRenderer() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void render(AnalysisResult result, Path outputPath) throws IOException {
        String template = loadTemplate();
        String json     = mapper.writeValueAsString(result);
        String html     = template.replace(PLACEHOLDER, json);
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/report.html")) {
            if (is == null) throw new IOException("템플릿 파일을 찾을 수 없습니다: /templates/report.html");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
