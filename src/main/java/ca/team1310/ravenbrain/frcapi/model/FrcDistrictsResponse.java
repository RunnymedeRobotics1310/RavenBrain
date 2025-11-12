package ca.team1310.ravenbrain.frcapi.model;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import lombok.Data;

/**
 * @author Tony Field
 * @since 2025-11-10 21:49
 */
@Serdeable
@Data
public class FrcDistrictsResponse {
  private long id;
  private boolean processed;
  private List<FrcDistrict> districts;
  private int districtCount;
}
