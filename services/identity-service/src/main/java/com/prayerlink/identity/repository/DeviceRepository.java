package com.prayerlink.identity.repository;

import com.prayerlink.common.config.TableNameResolver;
import com.prayerlink.identity.model.Device;
import java.util.Optional;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

@Repository
@RegisterReflectionForBinding({Device.class})
public class DeviceRepository {
    private final DynamoDbTable<Device> table;

    public DeviceRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
        this.table = enhancedClient.table(tableNameResolver.resolve("Devices"), Device.SCHEMA);
    }

    public void save(Device device) {
        table.putItem(device);
    }

    public Optional<Device> findById(java.util.UUID deviceId) {
        if (deviceId == null) return Optional.empty();
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(deviceId.toString()))));
    }
}
