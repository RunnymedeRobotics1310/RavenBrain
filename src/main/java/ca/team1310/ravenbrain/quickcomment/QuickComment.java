package ca.team1310.ravenbrain.quickcomment;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-03-31 00:26
 */
@MappedEntity(value = "RB_COMMENT")
@Serdeable
@Data
public class QuickComment {
  @Id long id;

  @MappedProperty("scoutname")
  String name;

  @MappedProperty("scoutrole")
  String role;

  @MappedProperty("teamnumber")
  int team;

  @MappedProperty("commenttimestamp")
  Instant timestamp;

  @MappedProperty("comment")
  String quickComment;
}
