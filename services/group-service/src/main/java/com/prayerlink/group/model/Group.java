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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Group {

  public static final TableSchema<Group> SCHEMA = StaticTableSchema.builder(Group.class)
    .newItemSupplier(Group::new)
    .addAttribute(String.class, a -> a.name("groupId")
      .getter(Group::getGroupId)
      .setter(Group::setGroupId)
      .tags(primaryPartitionKey()))
    .addAttribute(String.class, a -> a.name("name")
      .getter(Group::getName)
      .setter(Group::setName))
    .addAttribute(String.class, a -> a.name("description")
      .getter(Group::getDescription)
      .setter(Group::setDescription))
    .addAttribute(String.class, a -> a.name("passcode")
      .getter(Group::getPasscode)
      .setter(Group::setPasscode)
      .tags(secondaryPartitionKey("PasscodeIndex")))
    .addAttribute(Boolean.class, a -> a.name("optOutGeneral")
      .getter(Group::getOptOutGeneral)
      .setter(Group::setOptOutGeneral))
    .addAttribute(Instant.class, a -> a.name("createdAt")
      .getter(Group::getCreatedAt)
      .setter(Group::setCreatedAt))
    .build();

  private String groupId;
  private String name;
  private String description;
  private String passcode;
  private Boolean optOutGeneral;
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getGroupId() {
    return groupId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "PasscodeIndex")
  public String getPasscode() {
    return passcode;
  }
}
