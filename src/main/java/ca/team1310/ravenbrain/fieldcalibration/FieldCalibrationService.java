package ca.team1310.ravenbrain.fieldcalibration;

import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class FieldCalibrationService {

  private final FieldCalibrationRepository repository;

  public FieldCalibrationService(FieldCalibrationRepository repository) {
    this.repository = repository;
  }

  public Optional<FieldCalibration> findByYear(int year) {
    return repository.findByYear(year);
  }

  public void deleteByYear(int year) {
    repository.findByYear(year).ifPresent(repository::delete);
  }

  /**
   * Insert or update the calibration for the given year. Audit fields are stamped server-side and
   * any client-supplied values for {@code updatedAt} or {@code updatedByUserId} are ignored.
   */
  public FieldCalibration upsert(FieldCalibration incoming, long userId) {
    Instant now = Instant.now();
    return repository
        .findByYear(incoming.year())
        .map(
            existing ->
                repository.update(
                    new FieldCalibration(
                        existing.id(),
                        incoming.year(),
                        incoming.fieldLengthM(),
                        incoming.fieldWidthM(),
                        incoming.robotLengthM(),
                        incoming.robotWidthM(),
                        incoming.corner0X(),
                        incoming.corner0Y(),
                        incoming.corner1X(),
                        incoming.corner1Y(),
                        incoming.corner2X(),
                        incoming.corner2Y(),
                        incoming.corner3X(),
                        incoming.corner3Y(),
                        now,
                        userId)))
        .orElseGet(
            () ->
                repository.save(
                    new FieldCalibration(
                        null,
                        incoming.year(),
                        incoming.fieldLengthM(),
                        incoming.fieldWidthM(),
                        incoming.robotLengthM(),
                        incoming.robotWidthM(),
                        incoming.corner0X(),
                        incoming.corner0Y(),
                        incoming.corner1X(),
                        incoming.corner1Y(),
                        incoming.corner2X(),
                        incoming.corner2Y(),
                        incoming.corner3X(),
                        incoming.corner3Y(),
                        now,
                        userId)));
  }
}
