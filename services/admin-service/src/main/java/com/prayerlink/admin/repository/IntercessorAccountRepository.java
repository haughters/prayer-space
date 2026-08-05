package com.prayerlink.admin.repository;

import com.prayerlink.admin.model.IntercessorAccount;
import com.prayerlink.common.config.TableNameResolver;
import java.util.Optional;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

@Repository
@RegisterReflectionForBinding({IntercessorAccount.class})
public class IntercessorAccountRepository {
    private final DynamoDbTable<IntercessorAccount> table;

    public IntercessorAccountRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
        this.table = enhancedClient.table(tableNameResolver.resolve("IntercessorAccounts"), IntercessorAccount.SCHEMA);
    }

    public void save(IntercessorAccount account) {
        table.putItem(account);
    }

    public Optional<IntercessorAccount> findById(String email) {
        if (email == null) return Optional.empty();
        return Optional.ofNullable(table.getItem(
                r -> r.key(k -> k.partitionValue(email.toLowerCase().trim()))));
    }
}
