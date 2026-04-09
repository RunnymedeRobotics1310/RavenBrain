package ca.team1310.ravenbrain.telemetry;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_TELEMETRY_ENTRY")
@Serdeable
public record TelemetryEntry(
    @Id @GeneratedValue Long id,
    @MappedProperty("session_id") long sessionId,
    @MappedProperty("ts") Instant ts,
    @MappedProperty("entry_type") String entryType,
    @Nullable @MappedProperty("nt_key") String ntKey,
    @Nullable @MappedProperty("nt_type") String ntType,
    @Nullable @MappedProperty("nt_value") String ntValue,
    @Nullable @MappedProperty("fms_raw") Integer fmsRaw) {}
