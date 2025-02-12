package com.openFX.utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapConverter {
    /**
     * Converts a List of Maps with unknown key-value types to a List of Maps with String key-value pairs.
     * Null values are converted to empty strings.
     *
     * @param sourceRecords List of Map records to convert
     * @return List of converted Maps with String key-value pairs
     */
    public static List<Map<String, String>> convertToStringMaps(List<?> sourceRecords) {
        return sourceRecords.stream()
                .map(record -> {
                    Map<?, ?> mapRecord = (Map<?, ?>) record;
                    Map<String, String> converted = new HashMap<>();
                    
                    mapRecord.forEach((key, value) -> 
                        converted.put(
                            String.valueOf(key), 
                            value != null ? String.valueOf(value) : ""
                        )
                    );
                    
                    return converted;
                })
                .collect(Collectors.toList());
    }
}
