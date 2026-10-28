-- 게스트는 카카오 가입 전 상태이므로 NOT NULL 을 해제한다.
-- ddl-auto=update 는 기존 컬럼의 NOT NULL 을 절대 풀지 않는다.
ALTER TABLE users MODIFY COLUMN kakao_id VARCHAR(255) NULL;
ALTER TABLE users MODIFY COLUMN terms_agreed_at DATETIME NULL;

-- user_type 컬럼 추가.
-- 이미 존재할 수 있으므로 조건부로 실행한다. MySQL 8 에는 ADD COLUMN IF NOT EXISTS 가 없다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'user_type'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE users ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT ''MEMBER''',
    'DO 0');
PREPARE add_user_type FROM @ddl;
EXECUTE add_user_type;
DEALLOCATE PREPARE add_user_type;

-- Hibernate 가 먼저 컬럼을 만들었다면 DEFAULT 가 없어서
-- MySQL 이 기존 행을 첫 enum 값(GUEST)으로 채운다. 실제 회원을 되돌린다.
UPDATE users SET user_type = 'MEMBER' WHERE kakao_id IS NOT NULL;

-- 꼬리질문 타입 문자열 오타 교정
UPDATE questions SET type = 'FOLLOW_UP' WHERE type = 'Follow_UP';