package ca.team1310.ravenbrain.connect;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_REFRESH_TOKEN")
@Serdeable
public record RefreshToken(
    @Id @GeneratedValue long id,
    String username,
    @MappedProperty("refresh_token") String refreshToken,
    boolean revoked,
    @MappedProperty("date_created") Instant dateCreated) {}
