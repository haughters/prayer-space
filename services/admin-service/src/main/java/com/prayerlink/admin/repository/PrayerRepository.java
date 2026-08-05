package com.prayerlink.admin.repository;

import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.model.PrayerUpdate;
import com.prayerlink.common.config.TableNameResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
@RegisterReflectionForBinding({PrayerUpdate.class, Prayer.class})
public class PrayerRepository {
    private final DynamoDbTable<Prayer> table;
    private final DynamoDbTable<PrayerUpdate> updatesTable;
    private final DynamoDbIndex<Prayer> groupIdIndex;

    public PrayerRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
        this.table = enhancedClient.table(tableNameResolver.resolve("Prayers"), Prayer.SCHEMA);
        this.updatesTable = enhancedClient.table(tableNameResolver.resolve("PrayerUpdates"), PrayerUpdate.SCHEMA);
        this.groupIdIndex = this.table.index("GroupIdIndex");
    }

    public Optional<Prayer> findById(UUID prayerId) {
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(prayerId.toString()))));
    }

    public List<Prayer> searchPrayers(String status, UUID groupId, Instant fromDate, Instant toDate) {
        List<Prayer> results = new ArrayList<>();
        if (groupId != null) {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId.toString()));
            for (Page<Prayer> page : groupIdIndex.query(r -> r.queryConditional(queryConditional))) {
                results.addAll(page.items());
            }
        } else {
            for (Page<Prayer> page : table.scan()) {
                results.addAll(page.items());
            }
        }

        return results.stream()
                .filter(p -> status == null
                        || status.trim().isEmpty()
                        || "all".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(p.getStatus()))
                .filter(p -> fromDate == null || !p.getCreatedAt().isBefore(fromDate))
                .filter(p -> toDate == null || !p.getCreatedAt().isAfter(toDate))
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt())) // Descending
                .toList();
    }

    public List<PrayerUpdate> findUpdatesByPrayerId(UUID prayerId) {
        List<PrayerUpdate> updates = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(prayerId.toString()));
        try {
            for (Page<PrayerUpdate> page :
                    updatesTable.query(r -> r.queryConditional(queryConditional).scanIndexForward(false))) {
                updates.addAll(page.items());
            }
        } catch (Exception e) {
            // If table is empty or does not exist (e.g. during clean local setup before any updates)
        }
        return updates;
    }
}
