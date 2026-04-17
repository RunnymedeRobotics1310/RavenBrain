package ca.team1310.ravenbrain.fieldcalibration;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@MappedEntity(value = "RB_FIELD_CALIBRATION")
@Serdeable
public record FieldCalibration(
    @Id @GeneratedValue Long id,
    int year,
    @MappedProperty("field_length_m") double fieldLengthM,
    @MappedProperty("field_width_m") double fieldWidthM,
    @MappedProperty("robot_length_m") double robotLengthM,
    @MappedProperty("robot_width_m") double robotWidthM,
    @MappedProperty("corner0_x") double corner0X,
    @MappedProperty("corner0_y") double corner0Y,
    @MappedProperty("corner1_x") double corner1X,
    @MappedProperty("corner1_y") double corner1Y,
    @MappedProperty("corner2_x") double corner2X,
    @MappedProperty("corner2_y") double corner2Y,
    @MappedProperty("corner3_x") double corner3X,
    @MappedProperty("corner3_y") double corner3Y,
    @Nullable @MappedProperty("updated_at") Instant updatedAt,
    @Nullable @MappedProperty("updated_by_user_id") Long updatedByUserId) {}
