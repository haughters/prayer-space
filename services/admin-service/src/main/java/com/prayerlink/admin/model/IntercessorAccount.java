package com.prayerlink.admin.model;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.*;

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

  public static final TableSchema<IntercessorAccount> SCHEMA = StaticTableSchema.builder(IntercessorAccount.class)
    .newItemSupplier(IntercessorAccount::new)
    .addAttribute(String.class, a -> a.name("email")
      .getter(IntercessorAccount::getEmail)
      .setter(IntercessorAccount::setEmail)
      .tags(primaryPartitionKey()))
    .addAttribute(String.class, a -> a.name("passwordHash")
      .getter(IntercessorAccount::getPasswordHash)
      .setter(IntercessorAccount::setPasswordHash))
    .addAttribute(String.class, a -> a.name("name")
      .getter(IntercessorAccount::getName)
      .setter(IntercessorAccount::setName))
    .addAttribute(Instant.class, a -> a.name("createdAt")
      .getter(IntercessorAccount::getCreatedAt)
      .setter(IntercessorAccount::setCreatedAt))
    .build();

  private String email;
  private String passwordHash;
  private String name;
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getEmail() {
    return email;
  }
}
