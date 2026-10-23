CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(32) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    approved_at TIMESTAMP(3) NULL,
    approved_by VARCHAR(32) NULL,
    last_login_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS auth_permission (
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    module VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (code)
);

CREATE TABLE IF NOT EXISTS auth_role_permission (
    role VARCHAR(16) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (role, permission_code)
);
