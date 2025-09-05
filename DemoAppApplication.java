package com.example.demoapp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DemoappApplication implements CommandLineRunner {

    private final AppProperties props;
    private final WebClient webClient;

    public DemoappApplication(AppProperties props) {
        this.props = props;
        this.webClient = WebClient.builder().build();
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoappApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!StringUtils.hasText(props.getUrl())) {
            throw new IllegalArgumentException("app.url is required");
        }

        String jsonBody = resolveBody(props.getBodyFile(), props.getBody());
        if (!StringUtils.hasText(jsonBody)) {
            throw new IllegalArgumentException("Provide a JSON body via app.body or app.bodyFile");
        }

        WebClient.RequestBodySpec req = webClient
                .post()
                .uri(props.getUrl())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .header("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(props.getBearerToken())) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getBearerToken());
        }

        Map<String, String> extras = props.getExtraHeaders();
        if (extras != null) {
            for (var e : extras.entrySet()) {
                if (StringUtils.hasText(e.getKey()) && e.getValue() != null) {
                    req = req.header(e.getKey(), e.getValue());
                }
            }
        }

        String response = req.bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Response:");
        System.out.println(response);
    }

    private String resolveBody(String bodyFile, String inlineBody) throws Exception {
        if (StringUtils.hasText(bodyFile)) {
            return Files.readString(Path.of(bodyFile));
        }
        return inlineBody;
    }
}
