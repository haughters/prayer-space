package com.prayerlink.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TableNameResolver {
    private final String prefix;

    public TableNameResolver(@Value("${app.table-prefix:}") String prefix) {
        this.prefix = prefix;
    }

    public String resolve(String tableName) {
        return prefix.isEmpty() ? tableName : prefix + tableName;
    }
}
