package com.prayerlink.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.prayerlink.common.config.RestTemplateConfig;

@SpringBootApplication(scanBasePackages = "com.prayerlink")
@Import(RestTemplateConfig.class)
public class IdentityApplication {
  public static void main(String[] args) {
    SpringApplication.run(IdentityApplication.class, args);
  }
}
