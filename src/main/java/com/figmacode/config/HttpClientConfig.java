package com.figmacode.config;

import java.net.ProxySelector;
import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    /**
     * One shared JDK client for Bitbucket and Figma. It honours the JVM proxy settings
     * (https.proxyHost etc.) and the JVM trust store, which is how a corporate CA gets trusted.
     */
    @Bean
    HttpClient httpClient(AppProperties props) {
        return HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(props.getBitbucket().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
