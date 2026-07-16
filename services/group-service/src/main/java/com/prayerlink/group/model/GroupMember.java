package com.prayerlink.group.model;
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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GroupMember {

  public static final TableSchema<GroupMember> SCHEMA = StaticTableSchema.builder(GroupMember.class)
    .newItemSupplier(GroupMember::new)
    .addAttribute(String.class, a -> a.name("groupId")
      .getter(GroupMember::getGroupId)
      .setter(GroupMember::setGroupId)
      .tags(primaryPartitionKey()))
    .addAttribute(String.class, a -> a.name("memberId")
      .getter(GroupMember::getMemberId)
      .setter(GroupMember::setMemberId)
      .tags(primarySortKey()))
    .addAttribute(String.class, a -> a.name("name")
      .getter(GroupMember::getName)
      .setter(GroupMember::setName))
    .addAttribute(String.class, a -> a.name("email")
      .getter(GroupMember::getEmail)
      .setter(GroupMember::setEmail)
      .tags(secondaryPartitionKey("EmailIndex")))
    .addAttribute(Boolean.class, a -> a.name("bounced")
      .getter(GroupMember::getBounced)
      .setter(GroupMember::setBounced))
    .addAttribute(Instant.class, a -> a.name("addedAt")
      .getter(GroupMember::getAddedAt)
      .setter(GroupMember::setAddedAt))
    .build();

  private String groupId;
  private String memberId;
  private String name;
  private String email;
  private Boolean bounced;
  private Instant addedAt;

  @DynamoDbPartitionKey
  public String getGroupId() {
    return groupId;
  }

  @DynamoDbSortKey
  public String getMemberId() {
    return memberId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "EmailIndex")
  public String getEmail() {
    return email;
  }
}
