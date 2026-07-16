package com.prayerlink.group.repository;

import com.prayerlink.group.model.Group;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import com.prayerlink.common.config.TableNameResolver;

@Repository
@RegisterReflectionForBinding({Group.class})
public class GroupRepository {
  private final DynamoDbTable<Group> table;
  private final DynamoDbIndex<Group> passcodeIndex;

  public GroupRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
    this.table = enhancedClient.table(tableNameResolver.resolve("Groups"), Group.SCHEMA);
    this.passcodeIndex = this.table.index("PasscodeIndex");
  }

  public void save(Group group) {
    table.putItem(group);
  }

  public Optional<Group> findById(String groupId) {
    return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(groupId))));
  }

  public Optional<Group> findByPasscode(String passcode) {
    QueryConditional queryConditional =
        QueryConditional.keyEqualTo(k -> k.partitionValue(passcode));
    for (Page<Group> page : passcodeIndex.query(r -> r.queryConditional(queryConditional))) {
      for (Group group : page.items()) {
        return Optional.of(group);
      }
    }
    return Optional.empty();
  }

  public List<Group> findAll() {
    List<Group> groups = new ArrayList<>();
    for (Page<Group> page : table.scan()) {
      groups.addAll(page.items());
    }
    return groups;
  }

  public void delete(String groupId) {
    table.deleteItem(r -> r.key(k -> k.partitionValue(groupId)));
  }
}
