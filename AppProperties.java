package com.example.demoapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String url;
    private String bearerToken;
    private Map<String, String> extraHeaders;
    private String body;
    private String bodyFile;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }

    public Map<String, String> getExtraHeaders() { return extraHeaders; }
    public void setExtraHeaders(Map<String, String> extraHeaders) { this.extraHeaders = extraHeaders; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getBodyFile() { return bodyFile; }
    public void setBodyFile(String bodyFile) { this.bodyFile = bodyFile; }
}
