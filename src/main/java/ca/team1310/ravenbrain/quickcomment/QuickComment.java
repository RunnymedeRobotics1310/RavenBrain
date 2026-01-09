package ca.team1310.ravenbrain.quickcomment;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/**
 * @author Tony Field
 * @since 2025-03-31 00:26
 */
@MappedEntity(value = "RB_COMMENT")
@Serdeable
public record QuickComment(
    @Id Long id,
    @MappedProperty("userid") long userId,
    @MappedProperty("scoutrole") String role,
    @MappedProperty("teamnumber") int team,
    @MappedProperty("commenttimestamp") Instant timestamp,
    @MappedProperty("comment") String quickComment) {}
