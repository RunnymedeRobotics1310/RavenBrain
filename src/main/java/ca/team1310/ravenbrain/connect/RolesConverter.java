package ca.team1310.ravenbrain.connect;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@Introspected
public class RolesConverter implements AttributeConverter<List<String>, String> {

  @Override
  public String convertToPersistedValue(List<String> entityValue, ConversionContext context) {
    if (entityValue == null) return null;
    return String.join(",", entityValue);
  }

  @Override
  public List<String> convertToEntityValue(String persistedValue, ConversionContext context) {
    if (persistedValue == null || persistedValue.isEmpty()) return List.of();
    return Arrays.stream(persistedValue.split(",")).map(String::trim).collect(Collectors.toList());
  }
}
