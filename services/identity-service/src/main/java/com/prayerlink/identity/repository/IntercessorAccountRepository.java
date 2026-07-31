package com.prayerlink.identity.repository;

import com.prayerlink.common.config.TableNameResolver;
import com.prayerlink.identity.model.IntercessorAccount;
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

    public Optional<IntercessorAccount> findById(java.util.UUID accountId) {
        if (accountId == null) return Optional.empty();
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(accountId.toString()))));
    }

    public Optional<IntercessorAccount> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return table.scan().items().stream()
                .filter(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(email.trim()))
                .findFirst();
    }
}
