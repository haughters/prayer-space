package com.prayerlink.prayer.repository;

import com.prayerlink.common.config.TableNameResolver;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
@RegisterReflectionForBinding({Prayer.class})
public class PrayerRepository {
    private final DynamoDbTable<Prayer> table;
    private final DynamoDbTable<PrayerUpdate> updateTable;
    private final DynamoDbIndex<Prayer> deviceIdIndex;
    private final DynamoDbIndex<Prayer> groupIdIndex;
    private final DynamoDbClient rawClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private final TableNameResolver tableNameResolver;

    public PrayerRepository(
            DynamoDbEnhancedClient enhancedClient, DynamoDbClient rawClient, TableNameResolver tableNameResolver) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(tableNameResolver.resolve("Prayers"), Prayer.SCHEMA);
        this.updateTable = enhancedClient.table(tableNameResolver.resolve("PrayerUpdates"), PrayerUpdate.SCHEMA);
        this.deviceIdIndex = this.table.index("DeviceIdIndex");
        this.groupIdIndex = this.table.index("GroupIdIndex");
        this.rawClient = rawClient;
        this.tableNameResolver = tableNameResolver;
    }

    public void save(Prayer prayer) {
        table.putItem(prayer);
    }

    public void savePrayerAndUpdate(Prayer prayer, PrayerUpdate update) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(
                        table,
                        TransactPutItemEnhancedRequest.builder(Prayer.class)
                                .item(prayer)
                                .build())
                .addPutItem(
                        updateTable,
                        TransactPutItemEnhancedRequest.builder(PrayerUpdate.class)
                                .item(update)
                                .build())
                .build();
        enhancedClient.transactWriteItems(request);
    }

    public void recordPrayer(UUID prayerId, String email) {
        java.util.Map<String, AttributeValue> key = java.util.Map.of(
                "prayerId", AttributeValue.builder().s(prayerId.toString()).build());

        java.util.Map<String, AttributeValue> expressionAttributeValues = java.util.Map.of(
                ":one", AttributeValue.builder().n("1").build(),
                ":zero", AttributeValue.builder().n("0").build(),
                ":emailSet",
                        AttributeValue.builder().ss(java.util.List.of(email)).build(),
                ":emailSingle", AttributeValue.builder().s(email).build(),
                ":now",
                        AttributeValue.builder()
                                .s(java.time.Instant.now().toString())
                                .build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableNameResolver.resolve("Prayers"))
                .key(key)
                .updateExpression(
                        "SET prayedForCount = if_not_exists(prayedForCount, :zero) + :one, updatedAt = :now ADD prayedByEmails :emailSet")
                .conditionExpression(
                        "attribute_not_exists(prayedByEmails) OR NOT contains(prayedByEmails, :emailSingle)")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        rawClient.updateItem(request);
    }

    public Optional<Prayer> findById(UUID prayerId) {
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(prayerId.toString()))));
    }

    public List<Prayer> findByDeviceId(UUID deviceId) {
        List<Prayer> prayers = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(deviceId.toString()));
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // descending
                .build();
        for (Page<Prayer> page : deviceIdIndex.query(request)) {
            prayers.addAll(page.items());
        }
        return prayers;
    }

    public List<Prayer> findByGroupId(UUID groupId) {
        List<Prayer> prayers = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId.toString()));
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .build();
        for (Page<Prayer> page : groupIdIndex.query(request)) {
            prayers.addAll(page.items());
        }
        return prayers;
    }

    public List<Prayer> findByGroupIdAndStatus(UUID groupId, PrayerStatus status) {
        List<Prayer> prayers = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId.toString()));

        Expression filterExpression = Expression.builder()
                .expression("#s = :status")
                .putExpressionName("#s", "status")
                .putExpressionValue(
                        ":status", AttributeValue.builder().s(status.name()).build())
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .scanIndexForward(false)
                .build();
        for (Page<Prayer> page : groupIdIndex.query(request)) {
            prayers.addAll(page.items());
        }
        return prayers;
    }
}
