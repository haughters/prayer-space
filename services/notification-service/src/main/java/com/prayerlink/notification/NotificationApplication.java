package com.prayerlink.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.prayerlink.common.config.RestTemplateConfig;

@SpringBootApplication(scanBasePackages = "com.prayerlink")
@Import(RestTemplateConfig.class)
public class NotificationApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationApplication.class, args);
  }
}
