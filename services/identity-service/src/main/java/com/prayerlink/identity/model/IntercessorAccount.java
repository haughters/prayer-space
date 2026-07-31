package com.prayerlink.identity.model;

import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.*;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class IntercessorAccount {

    public static final TableSchema<IntercessorAccount> SCHEMA = StaticTableSchema.builder(IntercessorAccount.class)
            .newItemSupplier(IntercessorAccount::new)
            .addAttribute(
                    UUID.class,
                    a -> a.name("accountId")
                            .getter(IntercessorAccount::getAccountId)
                            .setter(IntercessorAccount::setAccountId)
                            .tags(primaryPartitionKey()))
            .addAttribute(
                    String.class,
                    a -> a.name("email").getter(IntercessorAccount::getEmail).setter(IntercessorAccount::setEmail))
            .addAttribute(
                    String.class,
                    a -> a.name("passwordHash")
                            .getter(IntercessorAccount::getPasswordHash)
                            .setter(IntercessorAccount::setPasswordHash))
            .addAttribute(
                    String.class,
                    a -> a.name("name").getter(IntercessorAccount::getName).setter(IntercessorAccount::setName))
            .addAttribute(
                    Instant.class,
                    a -> a.name("createdAt")
                            .getter(IntercessorAccount::getCreatedAt)
                            .setter(IntercessorAccount::setCreatedAt))
            .build();

    private UUID accountId;
    private String email;
    private String passwordHash;
    private String name;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public UUID getAccountId() {
        return accountId;
    }
}
