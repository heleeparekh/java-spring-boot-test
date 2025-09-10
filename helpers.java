setUsageFromResponse(span, resp);

private void setUsageFromResponse(Span span, String respJson) {
  try {
    JsonNode r = mapper.readTree(respJson);
    JsonNode usage = r.path("usage");
    if (!usage.isObject() && r.has("choices") && r.get("choices").isArray()) {
      usage = r.get("choices").get(0).path("usage");
    }
    long inTok  = usage.path("input_tokens").asLong(usage.path("prompt_tokens").asLong(0));
    long outTok = usage.path("output_tokens").asLong(usage.path("completion_tokens").asLong(0));
    long totTok = usage.path("total_tokens").asLong(inTok + outTok);

    span.setAttribute("llm.usage.input_tokens", inTok);
    span.setAttribute("llm.usage.output_tokens", outTok);
    span.setAttribute("llm.usage.total_tokens", totTok);

    // optional synonyms
    span.setAttribute("llm.usage.prompt_tokens", inTok);
    span.setAttribute("llm.usage.completion_tokens", outTok);
  } catch (Exception ignored) {}
}
