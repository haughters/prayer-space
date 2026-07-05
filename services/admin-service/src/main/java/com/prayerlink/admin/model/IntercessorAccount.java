package com.prayerlink.admin.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class IntercessorAccount {
  private String email;
  private String passwordHash;
  private String name;
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getEmail() {
    return email;
  }
}
