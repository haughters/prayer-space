package com.prayerlink.identity.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.identity.model.Device;
import com.prayerlink.identity.model.IntercessorAccount;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import com.prayerlink.common.config.TableNameResolver;

public class RepositoryTests {

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<Device> deviceTable;
    private DeviceRepository deviceRepository;

    private DynamoDbTable<IntercessorAccount> accountTable;
    private IntercessorAccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        TableNameResolver tableNameResolver = new TableNameResolver("");
        
        deviceTable = mock(DynamoDbTable.class);
        when(enhancedClient.table(eq("Devices"), any(TableSchema.class))).thenReturn(deviceTable);
        deviceRepository = new DeviceRepository(enhancedClient, tableNameResolver);

        accountTable = mock(DynamoDbTable.class);
        when(enhancedClient.table(eq("IntercessorAccounts"), any(TableSchema.class))).thenReturn(accountTable);
        accountRepository = new IntercessorAccountRepository(enhancedClient, tableNameResolver);
    }

    @Test
    void testDeviceRepository() {
        Device dev = new Device();
        dev.setDeviceId("d-1");

        deviceRepository.save(dev);
        verify(deviceTable).putItem(dev);

        when(deviceTable.getItem(any(Consumer.class))).thenReturn(dev);
        Optional<Device> found = deviceRepository.findById("d-1");
        assertTrue(found.isPresent());
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

        Optional<IntercessorAccount> foundNull = accountRepository.findById(null);
        assertFalse(foundNull.isPresent());
    }
}
