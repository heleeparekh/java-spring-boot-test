package com.example.demoapp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
  private final Tracer tracer;
  private final ObjectMapper mapper = new ObjectMapper();

  public DemoappApplication(AppProperties props, Tracer tracer) {
    this.props = props;
    this.tracer = tracer;
    this.webClient = WebClient.builder().build();
  }

  public static void main(String[] args) {
    SpringApplication.run(DemoappApplication.class, args);
  }

  @Override
  public void run(String... args) {
    if (!StringUtils.hasText(props.getUrl())) {
      throw new IllegalArgumentException("app.url is required");
    }

    // Read body (file or inline) off the event loop
    Mono.fromCallable(() -> resolveBody(props.getBodyFile(), props.getBody()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(jsonBody -> {
          // Extract useful LLM metadata for tracing
          String model = extractModel(jsonBody);
          String provider = guessProviderFromHeaders(props.getExtraHeaders());
          String userPrompt = extractPrompt(jsonBody);
          String requestId = UUID.randomUUID().toString();

          // Build request
          WebClient.RequestBodySpec req = webClient
              .post()
              .uri(props.getUrl())
              .header("X-Request-Id", requestId)
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

          // Start span
          Span span = tracer.spanBuilder("chat").startSpan();
          Scope scope = span.makeCurrent();

          return req
              .bodyValue(jsonBody)
              .retrieve()
              .bodyToMono(String.class)
              .doOnSubscribe(s -> {
                // OpenInference attributes (string keys)
                span.setAttribute("openinference.span.kind", "LLM");
                span.setAttribute("llm.model_name", model);
                span.setAttribute("llm.provider", provider);
                span.setAttribute("request.id", requestId);

                // Structured input message
                span.setAttribute("llm.input_messages.0.message.role", "user");
                span.setAttribute("llm.input_messages.0.message.content", truncate(userPrompt, 4000));
              })
              .doOnNext(resp -> {
                // Structured output message
                span.setAttribute("llm.output_messages.0.message.role", "assistant");
                span.setAttribute("llm.output_messages.0.message.content", truncate(resp, 4000));
                span.setStatus(StatusCode.OK);

                System.out.println("Response:");
                System.out.println(resp);

                // Optional: parse usage/cost if your API returns it
                // setUsageFromResponse(span, resp);
              })
              .doOnError(err -> {
                span.recordException(err);
                span.setStatus(StatusCode.ERROR);
                System.err.println("Request failed: " + err.getMessage());
              })
              .doFinally(sig -> {
                scope.close();
                span.end();
              });
        })
        .subscribe(
            r -> { /* handled in doOnNext */ },
            err -> System.err.println("Pipeline error: " + err)
        );

    // Note: This is non-blocking. The app will keep running (as a normal Spring app).
    // If you want the JVM to exit after the single request finishes, you can:
    //  1) set spring.main.web-application-type=none and use a CountDownLatch, or
    //  2) call SpringApplication.exit(...) in the doFinally() above.
  }

  // ---------- Helpers ----------

  private String resolveBody(String bodyFile, String inlineBody) throws Exception {
    if (StringUtils.hasText(bodyFile)) {
      return Files.readString(Path.of(bodyFile));
    }
    return inlineBody;
  }

  private String extractModel(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      if (root.hasNonNull("model")) return root.get("model").asText();
      if (root.has("parameters") && root.get("parameters").hasNonNull("model")) {
        return root.get("parameters").get("model").asText();
      }
    } catch (Exception ignored) {}
    return "unknown";
  }

  private String extractPrompt(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      // Common schema: messages: [ {role, content}, ... ]
      if (root.has("messages") && root.get("messages").isArray()) {
        for (JsonNode m : root.get("messages")) {
          if ("user".equals(m.path("role").asText())) {
            return m.path("content").asText("");
          }
        }
      }
      // Or prompt: "..."
      if (root.hasNonNull("prompt")) return root.get("prompt").asText();
    } catch (Exception ignored) {}
    // Fallback: store whole JSON if no user content found
    return json;
  }

  private String guessProviderFromHeaders(Map<String, String> headers) {
    if (headers == null) return "internal";
    if (headers.containsKey("x-wf-client-id")) return "wf";
    if (headers.containsKey("x-provider")) return headers.get("x-provider");
    return "internal";
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }

  // Example usage parser (optional)
  /*
  private void setUsageFromResponse(Span span, String respJson) {
    try {
      JsonNode r = mapper.readTree(respJson);
      JsonNode usage = r.path("usage");
      if (usage.isObject()) {
        if (usage.has("prompt_tokens"))   span.setAttribute("llm.usage.input_tokens",  usage.get("prompt_tokens").asLong());
        if (usage.has("completion_tokens")) span.setAttribute("llm.usage.output_tokens", usage.get("completion_tokens").asLong());
        if (usage.has("total_tokens"))    span.setAttribute("llm.usage.total_tokens",   usage.get("total_tokens").asLong());
      }
    } catch (Exception ignored) {}
  }
  */
}
