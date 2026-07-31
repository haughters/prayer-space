package com.prayerlink.group.repository;

import com.prayerlink.common.config.TableNameResolver;
import com.prayerlink.group.model.GroupMember;
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
@RegisterReflectionForBinding({GroupMember.class})
public class GroupMemberRepository {
    private final DynamoDbTable<GroupMember> table;
    private final DynamoDbIndex<GroupMember> emailIndex;

    public GroupMemberRepository(DynamoDbEnhancedClient enhancedClient, TableNameResolver tableNameResolver) {
        this.table = enhancedClient.table(tableNameResolver.resolve("GroupMembers"), GroupMember.SCHEMA);
        this.emailIndex = this.table.index("EmailIndex");
    }

    public void save(GroupMember member) {
        table.putItem(member);
    }

    public Optional<GroupMember> findById(UUID groupId, UUID memberId) {
        return Optional.ofNullable(table.getItem(
                r -> r.key(k -> k.partitionValue(groupId.toString()).sortValue(memberId.toString()))));
    }

    public List<GroupMember> findByGroupId(UUID groupId) {
        List<GroupMember> members = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k -> k.partitionValue(groupId.toString()));
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

    public void delete(UUID groupId, UUID memberId) {
        table.deleteItem(r -> r.key(k -> k.partitionValue(groupId.toString()).sortValue(memberId.toString())));
    }
}
