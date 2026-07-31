package com.prayerlink.prayer.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class PrayerUpdate {

    public static final TableSchema<PrayerUpdate> SCHEMA = TableSchema.fromBean(PrayerUpdate.class);

    private UUID prayerId;
    private Instant updatedAt;
    private String updateText;
    private UUID updatedByDeviceId;

    @DynamoDbPartitionKey
    public UUID getPrayerId() {
        return prayerId;
    }

    @DynamoDbSortKey
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
