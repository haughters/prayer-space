package com.prayerlink.admin.repository;

import com.prayerlink.admin.model.Admin;
import com.prayerlink.admin.model.IntercessorAccount;
import com.prayerlink.admin.model.Prayer;
import com.prayerlink.admin.model.PrayerUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.common.config.TableNameResolver;

public class RepositoryTests {

    private DynamoDbTable<Admin> adminTable;
    private DynamoDbIndex<Admin> usernameIndex;
    private AdminRepository adminRepository;

    private DynamoDbTable<IntercessorAccount> accountTable;
    private IntercessorAccountRepository accountRepository;

    private DynamoDbTable<Prayer> prayerTable;
    private DynamoDbTable<PrayerUpdate> updatesTable;
    private DynamoDbIndex<Prayer> groupIdIndex;
    private PrayerRepository prayerRepository;

    @BeforeEach
    void setUp() {
        DynamoDbEnhancedClient enhancedClient = mock(DynamoDbEnhancedClient.class);
        TableNameResolver tableNameResolver = new TableNameResolver("");

        adminTable = mock(DynamoDbTable.class);
        usernameIndex = mock(DynamoDbIndex.class);
        when(enhancedClient.table(eq("Admins"), any(TableSchema.class))).thenReturn(adminTable);
        when(adminTable.index(eq("UsernameIndex"))).thenReturn(usernameIndex);
        adminRepository = new AdminRepository(enhancedClient, tableNameResolver);

        accountTable = mock(DynamoDbTable.class);
        when(enhancedClient.table(eq("IntercessorAccounts"), any(TableSchema.class))).thenReturn(accountTable);
        accountRepository = new IntercessorAccountRepository(enhancedClient, tableNameResolver);

        prayerTable = mock(DynamoDbTable.class);
        updatesTable = mock(DynamoDbTable.class);
        groupIdIndex = mock(DynamoDbIndex.class);
        when(enhancedClient.table(eq("Prayers"), any(TableSchema.class))).thenReturn(prayerTable);
        when(enhancedClient.table(eq("PrayerUpdates"), any(TableSchema.class))).thenReturn(updatesTable);
        when(prayerTable.index(eq("GroupIdIndex"))).thenReturn(groupIdIndex);
        prayerRepository = new PrayerRepository(enhancedClient, tableNameResolver);
    }

    @Test
    void testAdminRepository() {
        Admin admin = new Admin();
        admin.setAdminId("a-1");
        admin.setUsername("admin");
        admin.setRole("APP_ADMIN");

        adminRepository.save(admin);
        verify(adminTable).putItem(admin);

        when(adminTable.getItem(any(Consumer.class))).thenReturn(admin);
        Optional<Admin> found = adminRepository.findById("a-1");
        assertTrue(found.isPresent());

        // findByUsername
        Page<Admin> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(admin));
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Admin> mockIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(usernameIndex.query(any(Consumer.class))).thenReturn(mockIterable);

        Optional<Admin> foundUser = adminRepository.findByUsername("admin");
        assertTrue(foundUser.isPresent());

        // findAll / isEmpty / countAppAdmins
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Admin> mockScanIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockScanIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(mockScanIterable.items()).thenReturn(mock(software.amazon.awssdk.core.pagination.sync.SdkIterable.class));
        when(adminTable.scan()).thenReturn(mockScanIterable);
        when(adminTable.scan(any(Consumer.class))).thenReturn(mockScanIterable);
        
        List<Admin> all = adminRepository.findAll();
        assertEquals(1, all.size());
        
        // Mock non-empty return for scan().items().stream()
        software.amazon.awssdk.core.pagination.sync.SdkIterable mockSdkIterable = mock(software.amazon.awssdk.core.pagination.sync.SdkIterable.class);
        when(mockScanIterable.items()).thenReturn(mockSdkIterable);
        when(mockSdkIterable.stream()).thenAnswer(inv -> Stream.of(admin));
        
        assertFalse(adminRepository.isEmpty());
        assertEquals(1, adminRepository.countAppAdmins());

        // delete
        adminRepository.delete("a-1");
        verify(adminTable).deleteItem(any(Consumer.class));
    }

    @Test
    void testIntercessorAccountRepository() {
        IntercessorAccount account = new IntercessorAccount();
        account.setEmail("test@example.com");

        accountRepository.save(account);
        verify(accountTable).putItem(account);

        when(accountTable.getItem(any(Consumer.class))).thenReturn(account);
        Optional<IntercessorAccount> found = accountRepository.findById("test@example.com");
        assertTrue(found.isPresent());

        assertFalse(accountRepository.findById(null).isPresent());
    }

    @Test
    void testPrayerRepository() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId("p-1");
        prayer.setStatus("OPEN");
        prayer.setCreatedAt(Instant.now());

        when(prayerTable.getItem(any(Consumer.class))).thenReturn(prayer);
        Optional<Prayer> found = prayerRepository.findById("p-1");
        assertTrue(found.isPresent());

        // searchPrayers (groupId specified)
        Page<Prayer> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(prayer));
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Prayer> mockIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(groupIdIndex.query(any(Consumer.class))).thenReturn(mockIterable);

        List<Prayer> searchRes = prayerRepository.searchPrayers("OPEN", "g-1", null, null);
        assertEquals(1, searchRes.size());

        // searchPrayers (scan branch)
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<Prayer> mockScanIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockScanIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(prayerTable.scan()).thenReturn(mockScanIterable);

        List<Prayer> searchAll = prayerRepository.searchPrayers("all", "all", Instant.now().minusSeconds(10), Instant.now().plusSeconds(10));
        assertEquals(1, searchAll.size());

        // findUpdatesByPrayerId
        PrayerUpdate update = new PrayerUpdate();
        update.setPrayerId("p-1");
        Page<PrayerUpdate> mockUpdatePage = mock(Page.class);
        when(mockUpdatePage.items()).thenReturn(List.of(update));
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<PrayerUpdate> mockUpdateIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockUpdateIterable.iterator()).thenAnswer(inv -> List.of(mockUpdatePage).iterator());
        when(updatesTable.query(any(Consumer.class))).thenReturn(mockUpdateIterable);

        List<PrayerUpdate> updates = prayerRepository.findUpdatesByPrayerId("p-1");
        assertEquals(1, updates.size());
    }
}
