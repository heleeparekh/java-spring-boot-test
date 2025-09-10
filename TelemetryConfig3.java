package com.example.demoapp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
// If you prefer gRPC, you can use:
// import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfig {

  @Value("${arize.enabled:true}")
  private boolean enabled;

  @Value("${arize.endpoint:https://otlp.arize.com/v1/traces}")
  private String endpoint;

  // Note the default empty values to avoid PlaceholderResolutionException in tests
  @Value("${arize.space-id:}")
  private String spaceId;

  @Value("${arize.api-key:}")
  private String apiKey;

  @Value("${arize.model-id:spring-oi-demo}")
  private String modelId;

  @Value("${arize.model-version:0.1.0}")
  private String modelVersion;

  @Bean
  public OpenTelemetry openTelemetry() {

    Resource resource =
        Resource.getDefault().merge(
            Resource.create(
                Attributes.of(
                    AttributeKey.stringKey("service.name"), "spring-oi-demo",
                    AttributeKey.stringKey("service.version"), modelVersion,
                    AttributeKey.stringKey("model_id"), modelId,
                    AttributeKey.stringKey("model_version"), modelVersion)));

    SdkTracerProviderBuilder tpBuilder =
        SdkTracerProvider.builder().setResource(resource);

    boolean haveCreds = spaceId != null && !spaceId.isBlank()
                      && apiKey != null && !apiKey.isBlank();

    if (enabled && haveCreds) {
      OtlpHttpSpanExporter exporter =
          OtlpHttpSpanExporter.builder()
              .setEndpoint(endpoint)
              .addHeader("space_id", spaceId)
              .addHeader("api_key", apiKey)
              .build();

      tpBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
    }
    // Else: no exporter is added; spans are simply dropped. This keeps tests green.

    SdkTracerProvider tracerProvider = tpBuilder.build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
  }

  @Bean
  public Tracer tracer(OpenTelemetry otel) {
    return otel.getTracer("com.example.demoapp");
  }
}
