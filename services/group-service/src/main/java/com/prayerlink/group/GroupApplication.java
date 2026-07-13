package com.prayerlink.group;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.prayerlink")
public class GroupApplication {
  public static void main(String[] args) {
    SpringApplication.run(GroupApplication.class, args);
  }
}
