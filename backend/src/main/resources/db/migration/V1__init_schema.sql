-- SmartPark initial schema (H2 + MySQL compatible)

CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE parking_slots (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_number      VARCHAR(20)  NOT NULL UNIQUE,
    zone             VARCHAR(10)  NOT NULL,
    floor            INT          NOT NULL DEFAULT 1,
    hourly_rate      DOUBLE       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    maintenance_note VARCHAR(255)
);

CREATE TABLE reservations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    slot_id         BIGINT      NOT NULL,
    vehicle_number  VARCHAR(15) NOT NULL,
    entry_code      VARCHAR(10) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    reserved_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP,
    entry_time      TIMESTAMP,
    exit_time       TIMESTAMP,
    amount          DOUBLE,
    expected_hours  INT         NOT NULL DEFAULT 2,
    extension_hours INT         NOT NULL DEFAULT 0,
    warning_sent    BOOLEAN     NOT NULL DEFAULT FALSE,
    synthetic       BOOLEAN     NOT NULL DEFAULT FALSE,
    pass_covered    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservation_slot FOREIGN KEY (slot_id) REFERENCES parking_slots (id)
);
CREATE INDEX idx_reservations_user ON reservations (user_id);
CREATE INDEX idx_reservations_slot ON reservations (slot_id);
CREATE INDEX idx_reservations_status ON reservations (status);

CREATE TABLE vehicle_profiles (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    label          VARCHAR(60) NOT NULL,
    vehicle_number VARCHAR(15) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_vehicle_user_number UNIQUE (user_id, vehicle_number)
);

CREATE TABLE waitlist_entries (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    vehicle_number VARCHAR(15) NOT NULL,
    zone           VARCHAR(10),
    slot_type      VARCHAR(20),
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulfilled_at   TIMESTAMP,
    CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_waitlist_status ON waitlist_entries (status);

CREATE TABLE notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    message    VARCHAR(300) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_notifications_user ON notifications (user_id);

CREATE TABLE scheduled_bookings (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    vehicle_number VARCHAR(15) NOT NULL,
    zone           VARCHAR(10),
    slot_type      VARCHAR(20),
    start_time     TIMESTAMP   NOT NULL,
    expected_hours INT         NOT NULL,
    status         VARCHAR(20) NOT NULL,
    reservation_id BIGINT,
    slot_number    VARCHAR(20),
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_bookings_status_start ON scheduled_bookings (status, start_time);

CREATE TABLE season_passes (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    zone       VARCHAR(10),
    valid_from TIMESTAMP   NOT NULL,
    valid_until TIMESTAMP  NOT NULL,
    price      DOUBLE      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pass_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE audit_logs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_email VARCHAR(255),
    actor_role  VARCHAR(20),
    action     VARCHAR(60)  NOT NULL,
    target     VARCHAR(120),
    detail     VARCHAR(500),
    ip         VARCHAR(45),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_created ON audit_logs (created_at);
