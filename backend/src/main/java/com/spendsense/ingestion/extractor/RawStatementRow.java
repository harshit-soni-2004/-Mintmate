package com.spendsense.ingestion.extractor;

import java.util.Collections;
import java.util.Map;

/**
 * Internal intermediate representation of a single extracted statement row
 * before canonical normalization. Separates raw file parsing concerns from financial semantics.
 */
public record RawStatementRow(
    int rowIndex,
    Map<String, String> cellValues,
    String rawLine
) {
    public RawStatementRow {
        cellValues = cellValues != null ? Collections.unmodifiableMap(cellValues) : Collections.emptyMap();
    }

    public String get(String key) {
        return cellValues.get(key);
    }
}
