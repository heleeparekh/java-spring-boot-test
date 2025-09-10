package com.example.demoapp;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class TokenService {
  private final WebClient http = WebClient.builder().build();
  private final AppProperties props;

  public TokenService(AppProperties props) {
    this.props = props;
  }

  /** Non-blocking: returns a Mono that yields a fresh Apigee access token. */
  public Mono<String> getBearerTokenAsync() {
    // If Apigee settings are missing, fall back to static bearerToken (if any)
    if (props.getApigeeTokenUrl() == null ||
        props.getApigeeConsumerKey() == null ||
        props.getApigeeConsumerSecret() == null) {
      return Mono.justOrEmpty(props.getBearerToken());
    }

    String basic = Base64.getEncoder().encodeToString(
        (props.getApigeeConsumerKey() + ":" + props.getApigeeConsumerSecret())
            .getBytes(StandardCharsets.UTF_8));

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");

    return http.post()
        .uri(props.getApigeeTokenUrl())
        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(form)
        .retrieve()
        .bodyToMono(Map.class)
        .map(resp -> String.valueOf(resp.get("access_token")));
  }
}
