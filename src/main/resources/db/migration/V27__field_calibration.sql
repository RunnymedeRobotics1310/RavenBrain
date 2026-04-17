CREATE TABLE RB_FIELD_CALIBRATION
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    year               INT          NOT NULL UNIQUE,
    field_length_m     DOUBLE       NOT NULL,
    field_width_m      DOUBLE       NOT NULL,
    robot_length_m     DOUBLE       NOT NULL,
    robot_width_m      DOUBLE       NOT NULL,
    corner0_x          DOUBLE       NOT NULL,
    corner0_y          DOUBLE       NOT NULL,
    corner1_x          DOUBLE       NOT NULL,
    corner1_y          DOUBLE       NOT NULL,
    corner2_x          DOUBLE       NOT NULL,
    corner2_y          DOUBLE       NOT NULL,
    corner3_x          DOUBLE       NOT NULL,
    corner3_y          DOUBLE       NOT NULL,
    updated_at         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by_user_id BIGINT       NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
