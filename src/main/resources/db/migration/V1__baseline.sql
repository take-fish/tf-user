CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    approved_at DATETIME(3) NULL,
    approved_by VARCHAR(32) NULL,
    last_login_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username),
    KEY idx_user_account_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
