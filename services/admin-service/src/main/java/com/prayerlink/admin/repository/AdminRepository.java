package com.prayerlink.admin.repository;

import com.prayerlink.admin.model.Admin;
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
public class AdminRepository {
  private final DynamoDbTable<Admin> table;
  private final DynamoDbIndex<Admin> usernameIndex;

  public AdminRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
    this.table = enhancedClient.table(tableNameResolver.resolve("Admins"), TableSchema.fromBean(Admin.class));
    this.usernameIndex = this.table.index("UsernameIndex");
  }

  public void save(Admin admin) {
    table.putItem(admin);
  }

  public Optional<Admin> findById(String adminId) {
    return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(adminId))));
  }

  public Optional<Admin> findByUsername(String username) {
    QueryConditional queryConditional =
        QueryConditional.keyEqualTo(k -> k.partitionValue(username));
    for (Page<Admin> page : usernameIndex.query(r -> r.queryConditional(queryConditional))) {
      for (Admin admin : page.items()) {
        return Optional.of(admin);
      }
    }
    return Optional.empty();
  }

  public List<Admin> findAll() {
    List<Admin> admins = new ArrayList<>();
    for (Page<Admin> page : table.scan()) {
      admins.addAll(page.items());
    }
    return admins;
  }

  public boolean isEmpty() {
    return table.scan(r -> r.limit(1)).items().stream().findAny().isEmpty();
  }

  public long countAppAdmins() {
    return table.scan().items().stream()
        .filter(a -> "APP_ADMIN".equals(a.getRole()))
        .count();
  }

  public void delete(String adminId) {
    table.deleteItem(r -> r.key(k -> k.partitionValue(adminId)));
  }
}
