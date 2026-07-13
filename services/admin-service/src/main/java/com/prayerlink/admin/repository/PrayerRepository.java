package com.prayerlink.admin.repository;

import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.model.PrayerUpdate;
import java.time.Instant;
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

import com.prayerlink.common.config.TableNameResolver;

@Repository
public class PrayerRepository {
  private final DynamoDbTable<Prayer> table;
  private final DynamoDbTable<PrayerUpdate> updatesTable;
  private final DynamoDbIndex<Prayer> groupIdIndex;

  public PrayerRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
    this.table = enhancedClient.table(tableNameResolver.resolve("Prayers"), TableSchema.fromBean(Prayer.class));
    this.updatesTable = enhancedClient.table(tableNameResolver.resolve("PrayerUpdates"), TableSchema.fromBean(PrayerUpdate.class));
    this.groupIdIndex = this.table.index("GroupIdIndex");
  }

  public Optional<Prayer> findById(String prayerId) {
    return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(prayerId))));
  }

  public List<Prayer> searchPrayers(String status, String groupId, Instant fromDate, Instant toDate) {
    List<Prayer> results = new ArrayList<>();
    if (groupId != null && !groupId.trim().isEmpty() && !"all".equalsIgnoreCase(groupId)) {
      QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId));
      for (Page<Prayer> page : groupIdIndex.query(r -> r.queryConditional(queryConditional))) {
        results.addAll(page.items());
      }
    } else {
      for (Page<Prayer> page : table.scan()) {
        results.addAll(page.items());
      }
    }

    return results.stream()
        .filter(p -> status == null || status.trim().isEmpty() || "all".equalsIgnoreCase(status) || status.equalsIgnoreCase(p.getStatus()))
        .filter(p -> fromDate == null || !p.getCreatedAt().isBefore(fromDate))
        .filter(p -> toDate == null || !p.getCreatedAt().isAfter(toDate))
        .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // Descending
        .toList();
  }

  public List<PrayerUpdate> findUpdatesByPrayerId(String prayerId) {
    List<PrayerUpdate> updates = new ArrayList<>();
    QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(prayerId));
    try {
      for (Page<PrayerUpdate> page : updatesTable.query(r -> r.queryConditional(queryConditional).scanIndexForward(false))) {
        updates.addAll(page.items());
      }
    } catch (Exception e) {
      // If table is empty or does not exist (e.g. during clean local setup before any updates)
    }
    return updates;
  }
}
