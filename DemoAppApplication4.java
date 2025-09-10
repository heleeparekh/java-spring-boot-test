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
import java.time.ZoneOffset;
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
  private final TokenService tokenService;
  private final ObjectMapper mapper = new ObjectMapper();

  public DemoappApplication(AppProperties props, Tracer tracer, TokenService tokenService) {
    this.props = props;
    this.tracer = tracer;
    this.tokenService = tokenService;
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

    // Build inputs as Monos (non-blocking)
    Mono<String> jsonBodyMono =
        Mono.fromCallable(() -> resolveBody(props.getBodyFile(), props.getBody()))
            .subscribeOn(Schedulers.boundedElastic());

    Mono<String> tokenMono = tokenService.getBearerTokenAsync().defaultIfEmpty("");

    // Run both in parallel, then make the request
    Mono.zip(jsonBodyMono, tokenMono)
        .flatMap(tuple -> {
          String jsonBody = tuple.getT1();
          String token = tuple.getT2();

          // Extract LLM metadata
          String model = extractModel(jsonBody);
          String provider = guessProviderFromHeaders(props.getExtraHeaders());
          String userPrompt = extractPrompt(jsonBody);

          // Per-request IDs / dates
          String requestId = UUID.randomUUID().toString();
          String correlationId = UUID.randomUUID().toString();
          String wfRequestDate = DateTimeFormatter.RFC_1123_DATE_TIME
              .format(ZonedDateTime.now(ZoneOffset.UTC));

          // Prepare WebClient request
          WebClient.RequestBodySpec req = webClient
              .post()
              .uri(props.getUrl())
              .header("X-Request-Id", requestId)
              .header("x-wf-request-date", wfRequestDate)
              .header("x-correlation-id", correlationId)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

          if (StringUtils.hasText(token)) {
            req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
          }

          Map<String, String> extras = props.getExtraHeaders();
          if (extras != null) {
            for (var e : extras.entrySet()) {
              if (StringUtils.hasText(e.getKey()) && e.getValue() != null) {
                req = req.header(e.getKey(), e.getValue());
              }
            }
          }

          // Start span inside the reactive chain
          Span span = tracer.spanBuilder("chat").startSpan();
          Scope scope = span.makeCurrent();

          return req
              .bodyValue(jsonBody)
              .retrieve()
              .bodyToMono(String.class)
              .doOnSubscribe(s -> {
                // OpenInference attributes
                span.setAttribute("openinference.span.kind", "LLM");
                span.setAttribute("llm.model_name", model);
                span.setAttribute("llm.provider", provider);
                span.setAttribute("request.id", requestId);
                // input message
                span.setAttribute("llm.input_messages.0.message.role", "user");
                span.setAttribute("llm.input_messages.0.message.content", truncate(userPrompt, 4000));
              })
              .doOnNext(resp -> {
                // output message
                span.setAttribute("llm.output_messages.0.message.role", "assistant");
                span.setAttribute("llm.output_messages.0.message.content", truncate(resp, 4000));
                span.setStatus(StatusCode.OK);
                System.out.println("Response:\n" + resp);
                // setUsageFromResponse(span, resp); // optional if your API returns usage
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
            r -> { /* handled upstream */ },
            err -> System.err.println("Pipeline error: " + err)
        );

    // Note: no .block() — the app stays up like a normal Spring app.
    // If you want to exit after one request, use a CountDownLatch and count down in doFinally.
  }

  // ---------- helpers ----------

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
      if (root.has("messages") && root.get("messages").isArray()) {
        for (JsonNode m : root.get("messages")) {
          if ("user".equals(m.path("role").asText())) {
            return m.path("content").asText("");
          }
        }
      }
      if (root.hasNonNull("prompt")) return root.get("prompt").asText();
    } catch (Exception ignored) {}
    return json;
  }

  private String guessProviderFromHeaders(Map<String,String> headers) {
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
        if (usage.has("prompt_tokens"))    span.setAttribute("llm.usage.input_tokens",  usage.get("prompt_tokens").asLong());
        if (usage.has("completion_tokens"))span.setAttribute("llm.usage.output_tokens", usage.get("completion_tokens").asLong());
        if (usage.has("total_tokens"))     span.setAttribute("llm.usage.total_tokens",  usage.get("total_tokens").asLong());
      }
    } catch (Exception ignored) {}
  }
  */
}
