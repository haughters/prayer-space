package com.prayerlink.prayer;

import com.prayerlink.common.config.RestTemplateConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.prayerlink")
@Import(RestTemplateConfig.class)
public class PrayerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrayerApplication.class, args);
    }
}
