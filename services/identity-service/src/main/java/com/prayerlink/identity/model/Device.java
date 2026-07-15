package com.prayerlink.identity.model;
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
public class Device {

  public static final TableSchema<Device> SCHEMA = StaticTableSchema.builder(Device.class)
    .newItemSupplier(Device::new)
    .addAttribute(String.class, a -> a.name("deviceId")
      .getter(Device::getDeviceId)
      .setter(Device::setDeviceId)
      .tags(primaryPartitionKey()))
    .addAttribute(Instant.class, a -> a.name("lastSeenAt")
      .getter(Device::getLastSeenAt)
      .setter(Device::setLastSeenAt))
    .addAttribute(Instant.class, a -> a.name("createdAt")
      .getter(Device::getCreatedAt)
      .setter(Device::setCreatedAt))
    .build();

  private String deviceId;
  private Instant lastSeenAt;
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getDeviceId() {
    return deviceId;
  }
}
