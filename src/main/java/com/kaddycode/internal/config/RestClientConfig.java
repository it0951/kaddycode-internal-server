package com.kaddycode.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setSupportedMediaTypes(List.of(
                new MediaType("application", "json", StandardCharsets.UTF_8),
                new MediaType("application", "*+json", StandardCharsets.UTF_8)
        ));

        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(0, converter);
                });
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}