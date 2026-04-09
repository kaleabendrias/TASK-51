-- Migration V2: Listings, Orders, Messaging, Blacklist, Points, Addresses, Notifications
USE booking_portal;

-- =====================================================
-- LISTINGS: photographer-owned service offerings
-- =====================================================
CREATE TABLE listings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    photographer_id   BIGINT        NOT NULL,
    title             VARCHAR(255)  NOT NULL,
    description       TEXT,
    category          VARCHAR(100),
    price             DECIMAL(10,2) NOT NULL,
    duration_minutes  INT           NOT NULL DEFAULT 60,
    location          VARCHAR(500),
    max_concurrent    INT           NOT NULL DEFAULT 1,
    active            BOOLEAN       DEFAULT TRUE,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (photographer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_listings_photographer ON listings(photographer_id);
CREATE INDEX idx_listings_category ON listings(category);
CREATE INDEX idx_listings_active ON listings(active);

-- =====================================================
-- TIME SLOTS: inventory for oversell prevention
-- =====================================================
CREATE TABLE time_slots (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_id      BIGINT    NOT NULL,
    slot_date       DATE      NOT NULL,
    start_time      TIME      NOT NULL,
    end_time        TIME      NOT NULL,
    capacity        INT       NOT NULL DEFAULT 1,
    booked_count    INT       NOT NULL DEFAULT 0,
    version         INT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    UNIQUE KEY uk_slot (listing_id, slot_date, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_timeslots_date ON time_slots(slot_date);

-- =====================================================
-- ORDERS: strict FSM lifecycle
-- =====================================================
CREATE TABLE orders (
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
    cancel_reason       TEXT,
    refund_amount       DECIMAL(10,2) DEFAULT 0.00,
    reschedule_from_id  BIGINT,
    address_id          BIGINT,
    notes               TEXT,
    payment_deadline    TIMESTAMP     NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id)     REFERENCES users(id),
    FOREIGN KEY (photographer_id) REFERENCES users(id),
    FOREIGN KEY (listing_id)      REFERENCES listings(id),
    FOREIGN KEY (time_slot_id)    REFERENCES time_slots(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_photographer ON orders(photographer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_payment_deadline ON orders(payment_deadline);

-- =====================================================
-- ORDER ACTIONS: audit trail for every state change
-- =====================================================
CREATE TABLE order_actions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30)  NOT NULL,
    performed_by    BIGINT       NOT NULL,
    detail          TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id)     REFERENCES orders(id),
    FOREIGN KEY (performed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_actions_order ON order_actions(order_id);

-- =====================================================
-- IDEMPOTENCY TOKENS: 10-minute validity per action
-- =====================================================
CREATE TABLE idempotency_tokens (
    token           VARCHAR(100) NOT NULL PRIMARY KEY,
    order_id        BIGINT,
    action          VARCHAR(50)  NOT NULL,
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_idemp_expires ON idempotency_tokens(expires_at);

-- =====================================================
-- ADDRESSES
-- =====================================================
CREATE TABLE addresses (
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
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_addresses_user ON addresses(user_id);

-- =====================================================
-- CONVERSATIONS & MESSAGES
-- =====================================================
CREATE TABLE conversations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    participant_one   BIGINT    NOT NULL,
    participant_two   BIGINT    NOT NULL,
    order_id          BIGINT,
    last_message_at   TIMESTAMP NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (participant_one) REFERENCES users(id),
    FOREIGN KEY (participant_two) REFERENCES users(id),
    FOREIGN KEY (order_id)        REFERENCES orders(id),
    UNIQUE KEY uk_conversation (participant_one, participant_two, order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_conv_p1 ON conversations(participant_one);
CREATE INDEX idx_conv_p2 ON conversations(participant_two);

CREATE TABLE messages (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id   BIGINT       NOT NULL,
    sender_id         BIGINT       NOT NULL,
    content           TEXT         NOT NULL,
    read_at           TIMESTAMP    NULL,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (sender_id)       REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_messages_conv ON messages(conversation_id);
CREATE INDEX idx_messages_unread ON messages(conversation_id, read_at);

-- =====================================================
-- NOTIFICATION QUEUE: local queued to-send records
-- =====================================================
CREATE TABLE notification_queue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    channel         VARCHAR(10)   NOT NULL COMMENT 'EMAIL or SMS',
    recipient       VARCHAR(255)  NOT NULL COMMENT 'email address or phone number (encrypted)',
    subject         VARCHAR(500),
    body            TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED, SENT, FAILED',
    retry_count     INT           DEFAULT 0,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    processed_at    TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notif_user ON notification_queue(user_id);
CREATE INDEX idx_notif_status ON notification_queue(status);

-- =====================================================
-- BLACKLIST: admin governance with audit
-- =====================================================
CREATE TABLE blacklist (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    reason          TEXT         NOT NULL,
    blacklisted_by  BIGINT       NOT NULL,
    duration_days   INT          NOT NULL DEFAULT 7,
    starts_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL,
    active          BOOLEAN      DEFAULT TRUE,
    lifted_by       BIGINT,
    lifted_at       TIMESTAMP    NULL,
    lift_reason     TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)        REFERENCES users(id),
    FOREIGN KEY (blacklisted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_blacklist_user ON blacklist(user_id);
CREATE INDEX idx_blacklist_active ON blacklist(active, expires_at);

-- =====================================================
-- POINTS / AWARDS LEDGER
-- =====================================================
CREATE TABLE points_ledger (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_points_user ON points_ledger(user_id);

-- Track current balance per user for quick reads
ALTER TABLE users ADD COLUMN points_balance INT NOT NULL DEFAULT 0;

INSERT INTO schema_version (version, description) VALUES (2, 'Listings, Orders FSM, Messaging, Blacklist, Points, Addresses, Notifications');
