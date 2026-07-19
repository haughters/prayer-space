package com.prayerlink.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import com.prayerlink.common.dto.*;
import com.prayerlink.common.event.*;

import com.prayerlink.common.exception.ErrorResponse;

@RegisterReflectionForBinding({
    DeviceDTO.class,
    GroupDTO.class,
    GroupMemberDTO.class,
    PrayerDTO.class,
    PrayerUpdateDTO.class,
    MemberAddedEvent.class,
    PrayerCreatedEvent.class,
    PrayerUpdatedEvent.class,
    ErrorResponse.class
})
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${aws.region:eu-west-1}") String region) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new AwsSigV4Interceptor(region));
        return restTemplate;
    }
}
