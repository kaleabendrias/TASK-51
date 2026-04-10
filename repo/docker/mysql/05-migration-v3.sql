-- Migration V3: Notification preferences, chat attachments, points rules, leaderboard support
USE booking_portal;

-- =====================================================
-- NOTIFICATION PREFERENCES per user
-- =====================================================
CREATE TABLE notification_preferences (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE,
    order_updates   BOOLEAN      DEFAULT TRUE,
    holds           BOOLEAN      DEFAULT TRUE,
    reminders       BOOLEAN      DEFAULT TRUE,
    approvals       BOOLEAN      DEFAULT TRUE,
    compliance      BOOLEAN      DEFAULT TRUE COMMENT 'Always true, cannot be muted',
    mute_non_critical BOOLEAN    DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- CHAT ATTACHMENTS (images in messages)
-- =====================================================
CREATE TABLE chat_attachments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id      BIGINT       NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size       BIGINT       NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- POINTS RULES: configurable award rules
-- =====================================================
CREATE TABLE points_rules (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(500),
    points          INT          NOT NULL,
    scope           VARCHAR(30)  NOT NULL DEFAULT 'INDIVIDUAL' COMMENT 'INDIVIDUAL, CLASS, DEPARTMENT, TEAM',
    trigger_event   VARCHAR(100) NOT NULL,
    active          BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- POINTS ADJUSTMENTS: admin manual with mandatory note
-- =====================================================
CREATE TABLE points_adjustments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    adjusted_by     BIGINT       NOT NULL,
    points          INT          NOT NULL,
    reason          TEXT         NOT NULL COMMENT 'Mandatory note for audit',
    balance_before  INT          NOT NULL,
    balance_after   INT          NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id),
    FOREIGN KEY (adjusted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_points_adj_user ON points_adjustments(user_id);

-- Add group/scope to users for team-based points
ALTER TABLE users ADD COLUMN department VARCHAR(100) DEFAULT NULL;
ALTER TABLE users ADD COLUMN team VARCHAR(100) DEFAULT NULL;

-- Seed default points rules
INSERT INTO points_rules (name, description, points, scope, trigger_event) VALUES
('ORDER_PAYMENT',   'Points awarded when order payment is recorded', 10, 'INDIVIDUAL', 'ORDER_PAID'),
('ORDER_COMPLETED',  'Bonus points when order completes successfully', 20, 'INDIVIDUAL', 'ORDER_COMPLETED'),
('FIRST_ORDER',      'Bonus for placing first order',                 50, 'INDIVIDUAL', 'FIRST_ORDER'),
('REFERRAL_BONUS',   'Points for referring a new customer',           30, 'INDIVIDUAL', 'REFERRAL'),
('TEAM_MILESTONE',   'Points when team reaches 100 orders',           100, 'TEAM',       'TEAM_MILESTONE'),
('DEPT_EXCELLENCE',  'Department quarterly excellence award',         200, 'DEPARTMENT', 'DEPT_AWARD');

-- Seed notification preferences for existing users
INSERT INTO notification_preferences (user_id) SELECT id FROM users;

INSERT INTO schema_version (version, description) VALUES (3, 'Notification preferences, chat attachments, points rules');
