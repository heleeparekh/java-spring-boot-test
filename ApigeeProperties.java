package com.example.demoapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apigee")
public class ApigeeProperties {
  private String tokenUrl;
  private String consumerKey;
  private String consumerSecret;

  public String getTokenUrl() { return tokenUrl; }
  public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
  public String getConsumerKey() { return consumerKey; }
  public void setConsumerKey(String consumerKey) { this.consumerKey = consumerKey; }
  public String getConsumerSecret() { return consumerSecret; }
  public void setConsumerSecret(String consumerSecret) { this.consumerSecret = consumerSecret; }
}
