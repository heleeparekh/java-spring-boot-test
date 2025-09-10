package com.example.demoapp;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class TokenService {
  private final WebClient http = WebClient.builder().build();
  private final AppProperties props;

  public TokenService(AppProperties props) { this.props = props; }

  /** Simple version: mint a fresh token every call (blocking). */
  public String getBearerToken() {
    if (props.getApigeeTokenUrl() == null ||
        props.getApigeeConsumerKey() == null ||
        props.getApigeeConsumerSecret() == null) {
      throw new IllegalStateException("Missing Apigee settings in app.* (tokenUrl/consumerKey/consumerSecret).");
    }

    String basic = Base64.getEncoder().encodeToString(
        (props.getApigeeConsumerKey() + ":" + props.getApigeeConsumerSecret()).getBytes(StandardCharsets.UTF_8));

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");

    Map resp = http.post()
        .uri(props.getApigeeTokenUrl())
        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(form)
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    if (resp == null || !resp.containsKey("access_token")) {
      throw new IllegalStateException("Apigee token response missing access_token");
    }
    return String.valueOf(resp.get("access_token"));
  }
}
