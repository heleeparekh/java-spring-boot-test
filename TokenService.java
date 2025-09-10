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
  private final ApigeeProperties cfg;

  public TokenService(ApigeeProperties cfg) { this.cfg = cfg; }

  /** Super-simple: mint a fresh token every time (no caching). */
  public String getBearerToken() {
    String basic = Base64.getEncoder().encodeToString(
        (cfg.getConsumerKey() + ":" + cfg.getConsumerSecret()).getBytes(StandardCharsets.UTF_8));

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");

    Map resp = http.post()
        .uri(cfg.getTokenUrl())
        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(form)
        .retrieve()
        .bodyToMono(Map.class)
        .block(); // ok here since your app already blocks

    return resp == null ? null : String.valueOf(resp.get("access_token"));
  }
}
