package com.example.demoapp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
// If you prefer HTTP instead:
// import io.opentelemetry.exporter.otlp.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
// Optional: uncomment next line ONLY if some code uses GlobalOpenTelemetry.get()
// import io.opentelemetry.api.GlobalOpenTelemetry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfig {

  @Bean
  public OpenTelemetry openTelemetry() {
    Resource resource = Resource.getDefault().merge(
        Resource.create(Attributes.of(
            AttributeKey.stringKey("service.name"), "spring-oi-demo",
            AttributeKey.stringKey("service.version"), "0.1.0")));

    // gRPC exporter to Phoenix
    var exporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint("http://localhost:4317")
        .build();

    // (Or use HTTP)
    // var exporter = OtlpHttpSpanExporter.builder()
    //     .setEndpoint("http://localhost:6006/v1/traces")
    //     .build();

    var tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
        .build();

    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        // keep W3C propagation
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();

    // Optional: set the global if any library uses GlobalOpenTelemetry.get()
    // GlobalOpenTelemetry.set(sdk);

    return sdk;
  }

  @Bean
  public Tracer tracer(OpenTelemetry otel) {
    return otel.getTracer("com.example.demoapp");
  }
}
