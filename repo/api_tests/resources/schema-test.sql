CREATE TABLE IF NOT EXISTS roles (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(500),
    role_id       BIGINT       NOT NULL,
    enabled       BOOLEAN      DEFAULT TRUE,
    points_balance INT         NOT NULL DEFAULT 0,
    department    VARCHAR(100),
    team          VARCHAR(100),
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);
CREATE TABLE IF NOT EXISTS services (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description CLOB,
    price       DECIMAL(10,2) NOT NULL,
    duration_minutes INT       NOT NULL DEFAULT 60,
    active      BOOLEAN       DEFAULT TRUE,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS bookings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id       BIGINT       NOT NULL,
    photographer_id   BIGINT,
    service_id        BIGINT       NOT NULL,
    booking_date      DATE         NOT NULL,
    start_time        TIME         NOT NULL,
    end_time          TIME         NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    location          VARCHAR(500),
    notes             CLOB,
    total_price       DECIMAL(10,2),
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (photographer_id) REFERENCES users(id),
    FOREIGN KEY (service_id) REFERENCES services(id)
);
CREATE TABLE IF NOT EXISTS attachments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id   BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size    BIGINT,
    uploaded_by  BIGINT       NOT NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS listings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    photographer_id   BIGINT        NOT NULL,
    title             VARCHAR(255)  NOT NULL,
    description       CLOB,
    category          VARCHAR(100),
    price             DECIMAL(10,2) NOT NULL,
    duration_minutes  INT           NOT NULL DEFAULT 60,
    location          VARCHAR(500),
    location_state    VARCHAR(50),
    location_city     VARCHAR(100),
    location_neighborhood VARCHAR(100),
    max_concurrent    INT           NOT NULL DEFAULT 1,
    active            BOOLEAN       DEFAULT TRUE,
    theme             VARCHAR(100),
    transport_mode    VARCHAR(50),
    rating            DECIMAL(3,2)  DEFAULT 0.00,
    rating_count      INT           DEFAULT 0,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (photographer_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS time_slots (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id      BIGINT    NOT NULL,
    slot_date       DATE      NOT NULL,
    start_time      TIME      NOT NULL,
    end_time        TIME      NOT NULL,
    capacity        INT       NOT NULL DEFAULT 1,
    booked_count    INT       NOT NULL DEFAULT 0,
    version         INT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number        VARCHAR(50)   NOT NULL UNIQUE,
    customer_id         BIGINT        NOT NULL,
    photographer_id     BIGINT        NOT NULL,
    listing_id          BIGINT        NOT NULL,
    time_slot_id        BIGINT        NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'CREATED',
    total_price         DECIMAL(10,2) NOT NULL,
    paid_amount         DECIMAL(10,2) DEFAULT 0.00,
    payment_reference   VARCHAR(255),
    cancel_reason       CLOB,
    refund_amount       DECIMAL(10,2) DEFAULT 0.00,
    reschedule_from_id  BIGINT,
    address_id          BIGINT,
    notes               CLOB,
    delivery_mode       VARCHAR(20)   NOT NULL DEFAULT 'ONSITE',
    payment_deadline    TIMESTAMP     NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (photographer_id) REFERENCES users(id),
    FOREIGN KEY (listing_id) REFERENCES listings(id),
    FOREIGN KEY (time_slot_id) REFERENCES time_slots(id)
);
CREATE TABLE IF NOT EXISTS order_actions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30)  NOT NULL,
    performed_by    BIGINT       NOT NULL,
    detail          CLOB,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (performed_by) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS idempotency_tokens (
    token           VARCHAR(100) NOT NULL PRIMARY KEY,
    order_id        BIGINT,
    action          VARCHAR(50)  NOT NULL,
    response_status INT,
    response_body   CLOB,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL
);
CREATE TABLE IF NOT EXISTS addresses (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    label         VARCHAR(100),
    street        VARCHAR(255) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100),
    postal_code   VARCHAR(20),
    country       VARCHAR(100) NOT NULL DEFAULT 'US',
    is_default    BOOLEAN      DEFAULT FALSE,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS conversations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    participant_one   BIGINT    NOT NULL,
    participant_two   BIGINT    NOT NULL,
    order_id          BIGINT,
    last_message_at   TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (participant_one) REFERENCES users(id),
    FOREIGN KEY (participant_two) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS messages (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id   BIGINT       NOT NULL,
    sender_id         BIGINT       NOT NULL,
    content           CLOB         NOT NULL,
    read_at           TIMESTAMP    NULL,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (sender_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS notification_queue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    channel         VARCHAR(10)   NOT NULL,
    recipient       VARCHAR(255)  NOT NULL,
    subject         VARCHAR(500),
    body            CLOB          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'QUEUED',
    retry_count     INT           NOT NULL DEFAULT 0,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    processed_at    TIMESTAMP     NULL,
    read_at         TIMESTAMP     NULL,
    archived        BOOLEAN       DEFAULT FALSE,
    terminal        BOOLEAN       DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS blacklist (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    reason          CLOB         NOT NULL,
    blacklisted_by  BIGINT       NOT NULL,
    duration_days   INT          NOT NULL DEFAULT 7,
    starts_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL,
    active          BOOLEAN      DEFAULT TRUE,
    lifted_by       BIGINT,
    lifted_at       TIMESTAMP    NULL,
    lift_reason     CLOB,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (blacklisted_by) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS points_ledger (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    points          INT           NOT NULL,
    balance_after   INT           NOT NULL,
    action          VARCHAR(100)  NOT NULL,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    description     VARCHAR(500),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS schema_version (
    version     INT          NOT NULL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS notification_preferences (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE,
    order_updates   BOOLEAN      DEFAULT TRUE,
    holds           BOOLEAN      DEFAULT TRUE,
    reminders       BOOLEAN      DEFAULT TRUE,
    approvals       BOOLEAN      DEFAULT TRUE,
    compliance      BOOLEAN      DEFAULT TRUE,
    mute_non_critical BOOLEAN    DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS chat_attachments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id      BIGINT       NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size       BIGINT       NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS points_rules (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(500),
    points          INT          NOT NULL,
    scope           VARCHAR(30)  NOT NULL DEFAULT 'INDIVIDUAL',
    trigger_event   VARCHAR(100) NOT NULL,
    active          BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS points_adjustments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    adjusted_by     BIGINT       NOT NULL,
    points          INT          NOT NULL,
    reason          CLOB         NOT NULL,
    balance_before  INT          NOT NULL,
    balance_after   INT          NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (adjusted_by) REFERENCES users(id)
);
