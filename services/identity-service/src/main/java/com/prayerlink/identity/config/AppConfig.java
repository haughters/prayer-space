package com.prayerlink.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {
  @Value("${app.security.bcrypt-cost:12}")
  private int bcryptCost = 12;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(bcryptCost);
  }
}
