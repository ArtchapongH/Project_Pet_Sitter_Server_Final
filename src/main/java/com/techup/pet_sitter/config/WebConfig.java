package com.techup.pet_sitter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final String PRODUCTION_FRONTEND = "https://pet-sitter-client-one.vercel.app";

    @Value("${app.cors.allowed-origin-patterns}")
    private String allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Stream.concat(
                        Stream.of(PRODUCTION_FRONTEND),
                        Arrays.stream(allowedOriginPatterns.split(","))
                )
                .map(String::trim)
                .map(origin -> origin.replaceAll("/+$", ""))
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);
        // Vercel forwards browser headers through its external rewrite, so the production
        // frontend origin must still be allowed by Spring even though the browser calls /api.
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
