package com.mazikox.metin_market_api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfiguration implements WebMvcConfigurer {
    private final List<String> allowedOriginPatterns;

    public CorsConfiguration(@Value("${app.cors.allowed-origins}") List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = List.copyOf(allowedOriginPatterns);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET")
                .allowedHeaders("*");
    }
}
