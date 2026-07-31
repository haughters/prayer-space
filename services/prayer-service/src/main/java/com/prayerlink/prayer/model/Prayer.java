package com.prayerlink.prayer.model;

import com.prayerlink.common.enums.PrayerStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Prayer {

    public static final TableSchema<Prayer> SCHEMA = TableSchema.fromBean(Prayer.class);

    private UUID prayerId;
    private UUID deviceId;
    private String prayerText;
    private UUID groupId;
    private UUID assignedGroupId;
    private PrayerStatus status;
    private Integer prayedForCount;
    private Set<String> prayedByEmails;
    private Instant createdAt;
    private Instant updatedAt;

    @DynamoDbPartitionKey
    public UUID getPrayerId() {
        return prayerId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "DeviceIdIndex")
    public UUID getDeviceId() {
        return deviceId;
    }

    @DynamoDbSecondarySortKey(indexNames = {"DeviceIdIndex", "GroupIdIndex"})
    public Instant getCreatedAt() {
        return createdAt;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GroupIdIndex")
    public UUID getAssignedGroupId() {
        return assignedGroupId;
    }
}
