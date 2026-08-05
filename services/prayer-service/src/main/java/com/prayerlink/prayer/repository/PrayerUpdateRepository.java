package com.prayerlink.prayer.repository;

import com.prayerlink.common.config.TableNameResolver;
import com.prayerlink.prayer.model.PrayerUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
@RegisterReflectionForBinding({PrayerUpdate.class})
public class PrayerUpdateRepository {
    private final DynamoDbTable<PrayerUpdate> table;

    public PrayerUpdateRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
        this.table = enhancedClient.table(tableNameResolver.resolve("PrayerUpdates"), PrayerUpdate.SCHEMA);
    }

    public void save(PrayerUpdate update) {
        table.putItem(update);
    }

    public List<PrayerUpdate> findByPrayerId(UUID prayerId) {
        List<PrayerUpdate> updates = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(prayerId.toString()));
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
