@Configuration
public class TelemetryConfig {

  @Value("${arize.endpoint:https://otlp.arize.com/v1/traces}")
  private String arizeEndpoint;

  @Value("${arize.space-id}")
  private String spaceId;

  @Value("${arize.api-key}")
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
                    AttributeKey.stringKey("model_id"), modelId,
                    AttributeKey.stringKey("model_version"), modelVersion)));

    var exporter =
        OtlpHttpSpanExporter.builder()
            .setEndpoint(arizeEndpoint)
            .addHeader("space_id", spaceId)   // required by Arize
            .addHeader("api_key", apiKey)     // required by Arize
            .build();

    var tracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .build();

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
