-- 기존 DB(운영·로컬)에만 적용하는 변경이다.
-- 빈 DB(신규 환경)에는 테이블이 아직 없으므로 모든 구문을 건너뛰고,
-- 테이블은 Hibernate(ddl-auto=update)가 현재 엔티티 기준으로 만든다.
-- 모든 구문은 멱등하다. MySQL 8 에는 IF NOT EXISTS 류 DDL 이 없어 동적 SQL 로 분기한다.

SET @has_users := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
);
SET @has_questions := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'questions'
);
SET @has_user_type := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'user_type'
);

-- 1. 게스트는 카카오 가입·약관 동의 전이므로 NOT NULL 을 해제한다.
--    ddl-auto=update 는 기존 컬럼의 NOT NULL 을 풀지 않는다.
SET @ddl := IF(@has_users > 0,
    'ALTER TABLE users MODIFY COLUMN kakao_id VARCHAR(255) NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(@has_users > 0,
    'ALTER TABLE users MODIFY COLUMN terms_agreed_at DATETIME(6) NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. user_type 이 없으면 추가한다. (운영에는 Hibernate 가 이미 만들어 두었다)
SET @ddl := IF(@has_users > 0 AND @has_user_type = 0,
    'ALTER TABLE users ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT ''MEMBER''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Hibernate 가 DEFAULT 없이 컬럼을 만들면 기존 행이 첫 enum 값(GUEST)으로 채워진다.
--    실제 회원을 되돌린다.
SET @ddl := IF(@has_users > 0,
    'UPDATE users SET user_type = ''MEMBER'' WHERE kakao_id IS NOT NULL',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 꼬리질문 타입 문자열 오타 교정
SET @ddl := IF(@has_questions > 0,
    'UPDATE questions SET type = ''FOLLOW_UP'' WHERE type = ''Follow_UP''',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;