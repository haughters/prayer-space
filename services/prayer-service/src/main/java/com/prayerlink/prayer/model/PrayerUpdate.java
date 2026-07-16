package com.prayerlink.prayer.model;
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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class PrayerUpdate {

  public static final TableSchema<PrayerUpdate> SCHEMA = StaticTableSchema.builder(PrayerUpdate.class)
    .newItemSupplier(PrayerUpdate::new)
    .addAttribute(String.class, a -> a.name("prayerId")
      .getter(PrayerUpdate::getPrayerId)
      .setter(PrayerUpdate::setPrayerId)
      .tags(primaryPartitionKey()))
    .addAttribute(Instant.class, a -> a.name("updatedAt")
      .getter(PrayerUpdate::getUpdatedAt)
      .setter(PrayerUpdate::setUpdatedAt)
      .tags(primarySortKey()))
    .addAttribute(String.class, a -> a.name("updateText")
      .getter(PrayerUpdate::getUpdateText)
      .setter(PrayerUpdate::setUpdateText))
    .addAttribute(String.class, a -> a.name("updatedByDeviceId")
      .getter(PrayerUpdate::getUpdatedByDeviceId)
      .setter(PrayerUpdate::setUpdatedByDeviceId))
    .build();

  private String prayerId;
  private Instant updatedAt;
  private String updateText;
  private String updatedByDeviceId;

  @DynamoDbPartitionKey
  public String getPrayerId() {
    return prayerId;
  }

  @DynamoDbSortKey
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
