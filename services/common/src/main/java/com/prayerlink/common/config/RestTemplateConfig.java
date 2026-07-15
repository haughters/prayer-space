package com.prayerlink.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${aws.region:eu-west-1}") String region) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new AwsSigV4Interceptor(region));
        return restTemplate;
    }
}
