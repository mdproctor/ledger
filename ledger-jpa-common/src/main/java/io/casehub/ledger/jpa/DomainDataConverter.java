package io.casehub.ledger.jpa;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DomainDataConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

    @Override
    public String convertToDatabaseColumn(final Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to serialize domainData", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(final String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to deserialize domainData", e);
        }
    }
}
