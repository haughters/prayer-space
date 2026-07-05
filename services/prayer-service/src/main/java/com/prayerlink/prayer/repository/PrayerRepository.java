package com.prayerlink.prayer.repository;

import com.prayerlink.prayer.model.Prayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class PrayerRepository {
  private final DynamoDbTable<Prayer> table;
  private final DynamoDbIndex<Prayer> deviceIdIndex;
  private final DynamoDbIndex<Prayer> groupIdIndex;
  private final DynamoDbClient rawClient;

  public PrayerRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient rawClient) {
    this.table = enhancedClient.table("Prayers", TableSchema.fromBean(Prayer.class));
    this.deviceIdIndex = this.table.index("DeviceIdIndex");
    this.groupIdIndex = this.table.index("GroupIdIndex");
    this.rawClient = rawClient;
  }

  public void save(Prayer prayer) {
    table.putItem(prayer);
  }

  public void recordPrayer(String prayerId, String email) {
    java.util.Map<String, AttributeValue> key = java.util.Map.of(
        "prayerId", AttributeValue.builder().s(prayerId).build()
    );

    java.util.Map<String, AttributeValue> expressionAttributeValues = java.util.Map.of(
        ":one", AttributeValue.builder().n("1").build(),
        ":zero", AttributeValue.builder().n("0").build(),
        ":emailSet", AttributeValue.builder().ss(java.util.List.of(email)).build(),
        ":emailSingle", AttributeValue.builder().s(email).build(),
        ":now", AttributeValue.builder().s(java.time.Instant.now().toString()).build()
    );

    UpdateItemRequest request = UpdateItemRequest.builder()
        .tableName("Prayers")
        .key(key)
        .updateExpression("SET prayedForCount = if_not_exists(prayedForCount, :zero) + :one, updatedAt = :now ADD prayedByEmails :emailSet")
        .conditionExpression("attribute_not_exists(prayedByEmails) OR NOT contains(prayedByEmails, :emailSingle)")
        .expressionAttributeValues(expressionAttributeValues)
        .build();

    rawClient.updateItem(request);
  }

  public Optional<Prayer> findById(String prayerId) {
    return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(prayerId))));
  }

  public List<Prayer> findByDeviceId(String deviceId) {
    List<Prayer> prayers = new ArrayList<>();
    QueryConditional queryConditional =
        QueryConditional.keyEqualTo(k -> k.partitionValue(deviceId));
    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .scanIndexForward(false) // descending
            .build();
    for (Page<Prayer> page : deviceIdIndex.query(request)) {
      prayers.addAll(page.items());
    }
    return prayers;
  }

  public List<Prayer> findByGroupId(String groupId) {
    List<Prayer> prayers = new ArrayList<>();
    QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId));
    QueryEnhancedRequest request =
        QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .scanIndexForward(false)
            .build();
    for (Page<Prayer> page : groupIdIndex.query(request)) {
      prayers.addAll(page.items());
    }
    return prayers;
  }
}
