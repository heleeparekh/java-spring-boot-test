private String extractAssistantText(String respJson) {
  try {
    JsonNode r = mapper.readTree(respJson);
    // OpenAI-like
    if (r.has("choices") && r.get("choices").isArray() && r.get("choices").size() > 0) {
      JsonNode msg = r.get("choices").get(0).path("message");
      if (!msg.isMissingNode()) return msg.path("content").asText(null);
      // older OpenAI: "text" on the choice
      String t = r.get("choices").get(0).path("text").asText(null);
      if (t != null) return t;
    }
    // Google/others patterns
    if (r.hasNonNull("output_text")) return r.get("output_text").asText();
    if (r.has("candidates") && r.get("candidates").isArray() && r.get("candidates").size() > 0) {
      String t = r.get("candidates").get(0).path("content").asText(null);
      if (t != null) return t;
    }
  } catch (Exception ignored) {}
  return null;
}

private void setUsageFromResponse(Span span, String respJson) {
  try {
    JsonNode r = mapper.readTree(respJson);
    JsonNode usage = r.path("usage");
    if (!usage.isObject()) return;

    // Common names
    long prompt = usage.path("prompt_tokens").asLong(usage.path("input_tokens").asLong(0));
    long completion = usage.path("completion_tokens").asLong(usage.path("output_tokens").asLong(0));
    long total = usage.path("total_tokens").asLong(prompt + completion);

    // Set BOTH naming styles for maximum compatibility
    span.setAttribute("llm.usage.prompt_tokens", prompt);
    span.setAttribute("llm.usage.completion_tokens", completion);
    span.setAttribute("llm.usage.total_tokens", total);

    span.setAttribute("llm.usage.input_tokens", prompt);
    span.setAttribute("llm.usage.output_tokens", completion);
  } catch (Exception ignored) {}
}
