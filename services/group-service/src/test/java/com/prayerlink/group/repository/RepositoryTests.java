package com.prayerlink.group.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.group.model.Group;
import com.prayerlink.group.model.GroupMember;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import com.prayerlink.common.config.TableNameResolver;

public class RepositoryTests {

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<Group> groupTable;
    private DynamoDbIndex<Group> passcodeIndex;
    private GroupRepository groupRepository;

    private DynamoDbTable<GroupMember> memberTable;
    private DynamoDbIndex<GroupMember> emailIndex;
    private GroupMemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        TableNameResolver tableNameResolver = new TableNameResolver("");
        
        groupTable = mock(DynamoDbTable.class);
        passcodeIndex = mock(DynamoDbIndex.class);
        when(enhancedClient.table(eq("Groups"), any(TableSchema.class))).thenReturn(groupTable);
        when(groupTable.index(eq("PasscodeIndex"))).thenReturn(passcodeIndex);
        groupRepository = new GroupRepository(enhancedClient, tableNameResolver);

        memberTable = mock(DynamoDbTable.class);
        emailIndex = mock(DynamoDbIndex.class);
        when(enhancedClient.table(eq("GroupMembers"), any(TableSchema.class))).thenReturn(memberTable);
        when(memberTable.index(eq("EmailIndex"))).thenReturn(emailIndex);
        memberRepository = new GroupMemberRepository(enhancedClient, tableNameResolver);
    }

    @Test
    void testGroupRepository() {
        Group group = new Group();
        group.setGroupId("g-1");
        group.setPasscode("123456");

        // save
        groupRepository.save(group);
        verify(groupTable).putItem(group);

        // findById
        when(groupTable.getItem(any(Consumer.class))).thenReturn(group);
        Optional<Group> found = groupRepository.findById("g-1");
        assertTrue(found.isPresent());
        assertEquals("g-1", found.get().getGroupId());

        // findByPasscode
        Page<Group> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(group));
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Group> mockIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(passcodeIndex.query(any(Consumer.class))).thenReturn(mockIterable);

        Optional<Group> foundPass = groupRepository.findByPasscode("123456");
        assertTrue(foundPass.isPresent());
        assertEquals("123456", foundPass.get().getPasscode());

        // findAll
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Group> mockScanIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockScanIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(groupTable.scan()).thenReturn(mockScanIterable);

        List<Group> all = groupRepository.findAll();
        assertEquals(1, all.size());

        // delete
        groupRepository.delete("g-1");
        verify(groupTable).deleteItem(any(Consumer.class));
    }

    @Test
    void testGroupMemberRepository() {
        GroupMember member = new GroupMember();
        member.setGroupId("g-1");
        member.setMemberId("m-1");
        member.setEmail("m@example.com");

        // save
        memberRepository.save(member);
        verify(memberTable).putItem(member);

        // findById
        when(memberTable.getItem(any(Consumer.class))).thenReturn(member);
        Optional<GroupMember> found = memberRepository.findById("g-1", "m-1");
        assertTrue(found.isPresent());

        // findByGroupId
        Page<GroupMember> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(member));
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<GroupMember> mockIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(memberTable.query(any(Consumer.class))).thenReturn(mockIterable);

        List<GroupMember> list = memberRepository.findByGroupId("g-1");
        assertEquals(1, list.size());

        // findByEmail
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<GroupMember> mockEmailIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockEmailIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(emailIndex.query(any(Consumer.class))).thenReturn(mockEmailIterable);

        List<GroupMember> listEmail = memberRepository.findByEmail("m@example.com");
        assertEquals(1, listEmail.size());

        // delete
        memberRepository.delete("g-1", "m-1");
        verify(memberTable).deleteItem(any(Consumer.class));
    }
}
