package com.prayerlink.prayer.repository;

import com.prayerlink.prayer.model.PrayerUpdate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import com.prayerlink.common.config.TableNameResolver;

@Repository
public class PrayerUpdateRepository {
  private final DynamoDbTable<PrayerUpdate> table;

  public PrayerUpdateRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
    this.table = enhancedClient.table(tableNameResolver.resolve("PrayerUpdates"), TableSchema.fromBean(PrayerUpdate.class));
  }

  public void save(PrayerUpdate update) {
    table.putItem(update);
  }

  public List<PrayerUpdate> findByPrayerId(String prayerId) {
    List<PrayerUpdate> updates = new ArrayList<>();
    QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(prayerId));
    QueryEnhancedRequest request = QueryEnhancedRequest.builder()
            .queryConditional(queryConditional)
            .scanIndexForward(false)
            .build();

    for (Page<PrayerUpdate> page : table.query(request)) {
      updates.addAll(page.items());
    }

    return updates;
  }
}
