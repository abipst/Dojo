package com.openFX.db;

import java.util.List;
import java.util.Map;

public interface DbService extends AutoCloseable {
    List<Map<String, Object>> executeQuery(String queryKey, Object... params);
    int executeUpdate(String queryKey, Object... params);
}
