package com.prayerlink.prayer.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
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
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import com.prayerlink.common.config.TableNameResolver;

public class RepositoryTests {

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbClient rawClient;
    
    private DynamoDbTable<Prayer> prayerTable;
    private DynamoDbIndex<Prayer> deviceIdIndex;
    private DynamoDbIndex<Prayer> groupIdIndex;
    private PrayerRepository prayerRepository;

    private DynamoDbTable<PrayerUpdate> updateTable;
    private PrayerUpdateRepository updateRepository;

    @BeforeEach
    void setUp() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        rawClient = mock(DynamoDbClient.class);
        TableNameResolver tableNameResolver = new TableNameResolver("");

        prayerTable = mock(DynamoDbTable.class);
        deviceIdIndex = mock(DynamoDbIndex.class);
        groupIdIndex = mock(DynamoDbIndex.class);
        when(enhancedClient.table(eq("Prayers"), any(TableSchema.class))).thenReturn(prayerTable);
        when(prayerTable.index(eq("DeviceIdIndex"))).thenReturn(deviceIdIndex);
        when(prayerTable.index(eq("GroupIdIndex"))).thenReturn(groupIdIndex);
        prayerRepository = new PrayerRepository(enhancedClient, rawClient, tableNameResolver);

        updateTable = mock(DynamoDbTable.class);
        when(enhancedClient.table(eq("PrayerUpdates"), any(TableSchema.class))).thenReturn(updateTable);
        updateRepository = new PrayerUpdateRepository(enhancedClient, tableNameResolver);
    }

    @Test
    void testPrayerRepository() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId("p-1");
        prayer.setDeviceId("d-1");
        prayer.setAssignedGroupId("g-1");

        // save
        prayerRepository.save(prayer);
        verify(prayerTable).putItem(prayer);

        // findById
        when(prayerTable.getItem(any(Consumer.class))).thenReturn(prayer);
        Optional<Prayer> found = prayerRepository.findById("p-1");
        assertTrue(found.isPresent());

        // findByDeviceId
        Page<Prayer> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(prayer));
        SdkIterable<Page<Prayer>> mockIterable = mock(SdkIterable.class);
        when(mockIterable.iterator()).thenAnswer(inv -> List.of(mockPage).iterator());
        when(deviceIdIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockIterable);

        List<Prayer> listDev = prayerRepository.findByDeviceId("d-1");
        assertEquals(1, listDev.size());

        // findByGroupId
        when(groupIdIndex.query(any(QueryEnhancedRequest.class))).thenReturn(mockIterable);
        List<Prayer> listGroup = prayerRepository.findByGroupId("g-1");
        assertEquals(1, listGroup.size());

        // recordPrayer
        prayerRepository.recordPrayer("p-1", "user@example.com");
        verify(rawClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void testPrayerUpdateRepository() {
        PrayerUpdate update = new PrayerUpdate();
        update.setPrayerId("p-1");

        // save
        updateRepository.save(update);
        verify(updateTable).putItem(update);

        // findByPrayerId
        Page<PrayerUpdate> mockPage = mock(Page.class);
        when(mockPage.items()).thenReturn(List.of(update));
        software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<PrayerUpdate> mockIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        when(mockIterable.iterator()).thenReturn(List.of(mockPage).iterator());
        when(updateTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockIterable);

        List<PrayerUpdate> list = updateRepository.findByPrayerId("p-1");
        assertEquals(1, list.size());
    }
}
