package com.prayerlink.identity.repository;

import com.prayerlink.identity.model.IntercessorAccount;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import com.prayerlink.common.config.TableNameResolver;

@Repository
public class IntercessorAccountRepository {
  private final DynamoDbTable<IntercessorAccount> table;

  public IntercessorAccountRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
    this.table = enhancedClient.table(tableNameResolver.resolve("IntercessorAccounts"), TableSchema.fromBean(IntercessorAccount.class));
  }

  public void save(IntercessorAccount account) {
    table.putItem(account);
  }

  public Optional<IntercessorAccount> findById(String email) {
    if (email == null) return Optional.empty();
    return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(email.toLowerCase().trim()))));
  }
}
