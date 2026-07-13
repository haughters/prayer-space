package com.prayerlink.identity.repository;

import com.prayerlink.identity.model.Device;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import com.prayerlink.common.config.TableNameResolver;

@Repository
public class DeviceRepository {
  private final DynamoDbTable<Device> table;

  public DeviceRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
    this.table = enhancedClient.table(tableNameResolver.resolve("Devices"), TableSchema.fromBean(Device.class));
  }

  public void save(Device device) {
    table.putItem(device);
  }

  public Optional<Device> findById(String deviceId) {
    return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(deviceId))));
  }
}
