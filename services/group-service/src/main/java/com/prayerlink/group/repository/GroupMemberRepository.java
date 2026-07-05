package com.prayerlink.group.repository;

import com.prayerlink.group.model.GroupMember;
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

@Repository
public class GroupMemberRepository {
  private final DynamoDbTable<GroupMember> table;
  private final DynamoDbIndex<GroupMember> emailIndex;

  public GroupMemberRepository(DynamoDbEnhancedClient enhancedClient) {
    this.table = enhancedClient.table("GroupMembers", TableSchema.fromBean(GroupMember.class));
    this.emailIndex = this.table.index("EmailIndex");
  }

  public void save(GroupMember member) {
    table.putItem(member);
  }

  public Optional<GroupMember> findById(String groupId, String memberId) {
    return Optional.ofNullable(
        table.getItem(r -> r.key(k -> k.partitionValue(groupId).sortValue(memberId))));
  }

  public List<GroupMember> findByGroupId(String groupId) {
    List<GroupMember> members = new ArrayList<>();
    QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId));
    for (Page<GroupMember> page : table.query(r -> r.queryConditional(queryConditional))) {
      members.addAll(page.items());
    }
    return members;
  }

  public List<GroupMember> findByEmail(String email) {
    List<GroupMember> members = new ArrayList<>();
    QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(email));
    for (Page<GroupMember> page : emailIndex.query(r -> r.queryConditional(queryConditional))) {
      members.addAll(page.items());
    }
    return members;
  }

  public void delete(String groupId, String memberId) {
    table.deleteItem(r -> r.key(k -> k.partitionValue(groupId).sortValue(memberId)));
  }
}
