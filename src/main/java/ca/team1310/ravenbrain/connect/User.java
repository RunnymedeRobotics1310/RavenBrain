package ca.team1310.ravenbrain.connect;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * @author Tony Field
 * @since 2026-01-03 11:52
 */
@MappedEntity(value = "RB_USER")
@Serdeable
public record User(
    @Id @GeneratedValue long id,
    String login,
    @MappedProperty("display_name") String displayName,
    @MappedProperty("password_hash") String passwordHash,
    boolean enabled,
    @MappedProperty("forgot_password") boolean forgotPassword,
    @TypeDef(type = DataType.STRING, converter = RolesConverter.class) List<String> roles) {}
