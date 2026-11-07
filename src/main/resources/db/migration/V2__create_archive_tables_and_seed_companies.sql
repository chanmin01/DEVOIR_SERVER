CREATE TABLE IF NOT EXISTS companies (
                                         id              BIGINT       NOT NULL AUTO_INCREMENT,
                                         corp_code       VARCHAR(8)   NULL,
    name            VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    source          VARCHAR(10)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_corp_code UNIQUE (corp_code),
    CONSTRAINT uk_company_normalized_name UNIQUE (normalized_name)
    );

CREATE TABLE IF NOT EXISTS company_aliases (
                                               id               BIGINT       NOT NULL AUTO_INCREMENT,
                                               company_id       BIGINT       NOT NULL,
                                               alias            VARCHAR(100) NOT NULL,
    normalized_alias VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_alias_normalized UNIQUE (normalized_alias),
    CONSTRAINT fk_alias_company FOREIGN KEY (company_id) REFERENCES companies (id)
    );

CREATE TABLE IF NOT EXISTS interview_archives (
                                                  id             BIGINT       NOT NULL AUTO_INCREMENT,
                                                  user_id        BIGINT       NOT NULL,
                                                  company_id     BIGINT       NOT NULL,
                                                  company_type   VARCHAR(20)  NOT NULL,
    stage          VARCHAR(20)  NOT NULL,
    format         VARCHAR(10)  NOT NULL,
    job_role       VARCHAR(30)  NULL,
    interviewed_at DATE         NOT NULL,
    memo           VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NULL,
    deleted_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX idx_archive_company_interviewed (company_id, interviewed_at),
    CONSTRAINT fk_archive_company FOREIGN KEY (company_id) REFERENCES companies (id)
    );

CREATE TABLE IF NOT EXISTS archive_questions (
                                                 id           BIGINT       NOT NULL AUTO_INCREMENT,
                                                 archive_id   BIGINT       NOT NULL,
                                                 seq          INT          NOT NULL,
                                                 content      VARCHAR(500) NOT NULL,
    like_count   INT          NOT NULL DEFAULT 0,
    report_count INT          NOT NULL DEFAULT 0,
    status       VARCHAR(10)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_question_archive (archive_id),
    INDEX idx_question_status_id (status, id),
    CONSTRAINT fk_question_archive FOREIGN KEY (archive_id) REFERENCES interview_archives (id)
    );

CREATE TABLE IF NOT EXISTS archive_likes (
                                             id          BIGINT      NOT NULL AUTO_INCREMENT,
                                             user_id     BIGINT      NOT NULL,
                                             question_id BIGINT      NOT NULL,
                                             created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_like_user_question UNIQUE (user_id, question_id),
    CONSTRAINT fk_like_question FOREIGN KEY (question_id) REFERENCES archive_questions (id)
    );

CREATE TABLE IF NOT EXISTS archive_reports (
                                               id          BIGINT      NOT NULL AUTO_INCREMENT,
                                               user_id     BIGINT      NOT NULL,
                                               question_id BIGINT      NOT NULL,
                                               reason      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_report_user_question UNIQUE (user_id, question_id),
    CONSTRAINT fk_report_question FOREIGN KEY (question_id) REFERENCES archive_questions (id)
    );

INSERT IGNORE INTO companies (name, normalized_name, source, created_at) VALUES
('삼성전자', '삼성전자', 'DART', NOW(6)),
('SK하이닉스', 'sk하이닉스', 'DART', NOW(6)),
('LG CNS', 'lgcns', 'DART', NOW(6)),
('네이버', '네이버', 'DART', NOW(6)),
('카카오', '카카오', 'DART', NOW(6)),
('쿠팡', '쿠팡', 'DART', NOW(6)),
('우아한형제들', '우아한형제들', 'DART', NOW(6)),
('라인플러스', '라인플러스', 'DART', NOW(6)),
('비바리퍼블리카', '비바리퍼블리카', 'DART', NOW(6)),
('카카오뱅크', '카카오뱅크', 'DART', NOW(6)),
('카카오페이', '카카오페이', 'DART', NOW(6)),
('신한은행', '신한은행', 'DART', NOW(6)),
('KB국민은행', 'kb국민은행', 'DART', NOW(6));

INSERT IGNORE INTO company_aliases (company_id, alias, normalized_alias)
SELECT id, 'kakao', 'kakao' FROM companies WHERE normalized_name = '카카오'
UNION ALL SELECT id, 'naver', 'naver' FROM companies WHERE normalized_name = '네이버'
UNION ALL SELECT id, 'coupang', 'coupang' FROM companies WHERE normalized_name = '쿠팡'
UNION ALL SELECT id, '배민', '배민' FROM companies WHERE normalized_name = '우아한형제들'
UNION ALL SELECT id, '배달의민족', '배달의민족' FROM companies WHERE normalized_name = '우아한형제들'
UNION ALL SELECT id, '토스', '토스' FROM companies WHERE normalized_name = '비바리퍼블리카'
UNION ALL SELECT id, 'toss', 'toss' FROM companies WHERE normalized_name = '비바리퍼블리카'
UNION ALL SELECT id, '삼성', '삼성' FROM companies WHERE normalized_name = '삼성전자'
UNION ALL SELECT id, '하이닉스', '하이닉스' FROM companies WHERE normalized_name = 'sk하이닉스'
UNION ALL SELECT id, '라인', '라인' FROM companies WHERE normalized_name = '라인플러스'
UNION ALL SELECT id, '국민은행', '국민은행' FROM companies WHERE normalized_name = 'kb국민은행';