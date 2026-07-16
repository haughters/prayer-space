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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Admin {

  public static final TableSchema<Admin> SCHEMA = StaticTableSchema.builder(Admin.class)
    .newItemSupplier(Admin::new)
    .addAttribute(String.class, a -> a.name("adminId")
      .getter(Admin::getAdminId)
      .setter(Admin::setAdminId)
      .tags(primaryPartitionKey()))
    .addAttribute(String.class, a -> a.name("username")
      .getter(Admin::getUsername)
      .setter(Admin::setUsername)
      .tags(secondaryPartitionKey("UsernameIndex")))
    .addAttribute(String.class, a -> a.name("passwordHash")
      .getter(Admin::getPasswordHash)
      .setter(Admin::setPasswordHash))
    .addAttribute(String.class, a -> a.name("role")
      .getter(Admin::getRole)
      .setter(Admin::setRole))
    .addAttribute(String.class, a -> a.name("groupId")
      .getter(Admin::getGroupId)
      .setter(Admin::setGroupId))
    .addAttribute(Instant.class, a -> a.name("createdAt")
      .getter(Admin::getCreatedAt)
      .setter(Admin::setCreatedAt))
    .build();

  private String adminId;
  private String username;
  private String passwordHash;
  private String role; // "APP_ADMIN" or "GROUP_ADMIN"
  private String groupId; // null for APP_ADMIN, uuid for GROUP_ADMIN
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getAdminId() {
    return adminId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "UsernameIndex")
  public String getUsername() {
    return username;
  }
}
