package com.prayerlink.notification;

import com.prayerlink.common.config.RestTemplateConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
        scanBasePackages = "com.prayerlink",
        excludeName = {
            "org.springframework.boot.webmvc.autoconfigure.WebMvcObservationAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration"
        })
@Import(RestTemplateConfig.class)
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
