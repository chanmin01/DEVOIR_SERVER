-- 기존 develop V2/V3 다음에 추가합니다.

CREATE TABLE pt_tasks (
                          id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                          company_id BIGINT NOT NULL,
                          company_type VARCHAR(20) NOT NULL,
                          code VARCHAR(100) NOT NULL,
                          task_version INT NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          content_json MEDIUMTEXT NOT NULL,
                          created_at DATETIME(6) NOT NULL,

                          CONSTRAINT uk_pt_task_code
                              UNIQUE (company_id, code),

                          CONSTRAINT fk_pt_task_company
                              FOREIGN KEY (company_id)
                                  REFERENCES companies(id),

                          INDEX ix_pt_task_company_status (
        company_id,
        status,
        id
    )
);

CREATE TABLE pt_sessions (
                             session_id BIGINT NOT NULL PRIMARY KEY,
                             task_id BIGINT NOT NULL,
                             creation_request_id VARCHAR(36) NOT NULL,
                             phase VARCHAR(20) NOT NULL,
                             preparation_minutes INT NOT NULL,
                             preparation_started_at DATETIME(6) NOT NULL,
                             preparation_deadline DATETIME(6) NOT NULL,
                             expires_at DATETIME(6) NULL,

                             CONSTRAINT uk_pt_creation_request
                                 UNIQUE (creation_request_id),

                             CONSTRAINT fk_pt_detail_task
                                 FOREIGN KEY (task_id)
                                     REFERENCES pt_tasks(id),

                             INDEX ix_pt_detail_expiry (expires_at)
);

-- 기존 V1 설명처럼 sessions는 신규 DB에서
-- Hibernate가 나중에 생성할 수 있습니다.
-- 따라서 여기서는 sessions FK를 선행 생성하지 않습니다.
-- 현재 ddl-auto=update가 @MapsId/@JoinColumn 매핑에 맞춰 생성합니다.

-- 기존 정규화 이름이나 별칭이 있으면 기업을 새로 만들지 않습니다.
-- 실제 DART 번호를 알 수 없으므로 임의 번호도 넣지 않습니다.

INSERT IGNORE INTO companies (
    name,
    normalized_name,
    source,
    created_at
)
SELECT
    '기업은행',
    '기업은행',
    'USER',
    CURRENT_TIMESTAMP(6)
    WHERE NOT EXISTS (
    SELECT 1
    FROM companies
    WHERE normalized_name IN (
        '기업은행',
        'ibk기업은행'
    )
)
AND NOT EXISTS (
    SELECT 1
    FROM company_aliases
    WHERE normalized_alias = '기업은행'
);

INSERT INTO pt_tasks (
    company_id,
    company_type,
    code,
    task_version,
    status,
    content_json,
    created_at
)
SELECT
    chosen.company_id,
    'FINANCE_IT',
    'SME_SERVICE_V1',
    1,
    'APPROVED',
    '{
      "title": "중소기업 위험 진단·금융상품 추천 서비스 고도화",
      "serviceDescription": "기업은행을 대상으로 연습하는 가상 서비스입니다. 중소기업 대표가 기업 정보를 제출하면 재무·거래 자료로 위험 요인을 확인하고 금융상품 후보를 추천받습니다. 담당자는 상담 보조자료로 사용하며 최종 대출 승인을 자동 결정하지 않습니다. 실제 기업은행 서비스나 기출문제에 관한 사실 진술이 아닙니다.",
      "currentFlow": [
        "기업정보 입력",
        "자료 제공 동의",
        "재무·거래 정보 조회",
        "위험 진단",
        "추천상품 확인",
        "상담 신청"
      ],
      "problemSituation": "가상 연습 상황에서 자료 제공 동의 중 이탈, 추천 이유 이해의 어려움, 결측 자료의 신뢰도 판단 문제, 담당자의 재확인 시간이 발생합니다. 우선 해결할 문제를 정하십시오.",
      "availableData": [
        "동의받아 수집한 재무정보",
        "거래 요약정보",
        "서비스 이용 로그",
        "상담 기록"
      ],
      "constraints": [
        "기존 상담 절차와 연결해야 합니다.",
        "새로운 외부 데이터 구매 없이 개선해야 합니다.",
        "보유 자료마다 최신성과 결측 수준이 다를 수 있습니다."
      ],
      "exerciseAssumptions": [
        "개발 인력 4명, 개발 기간 3개월은 연습용 가정입니다.",
        "서비스와 문제 상황은 연습용으로 구성했으며 실제 기업의 확인된 문제를 뜻하지 않습니다."
      ],
      "trackGuidance": {
        "SECURITY": "정보 접근·동의·오남용 방지 과정의 개선안을 제시하십시오.",
        "UI_UX": "입력·동의·결과 이해·상담 연결 과정의 개선안을 제시하십시오.",
        "AI": "진단·추천·설명 품질 개선안을 제시하십시오.",
        "DATA": "자료 품질·최신성·결측·검증 체계의 개선안을 제시하십시오."
      },
      "presentationRequirements": [
        "네 분야 중 하나를 선택하십시오.",
        "핵심 문제와 우선순위의 근거를 설명하십시오.",
        "대안 비교와 선택 이유를 설명하십시오.",
        "실행 순서와 위험을 설명하십시오.",
        "기대 효과의 검증 방법을 설명하십시오.",
        "제공되지 않은 수치는 가정임을 밝혀 5분 안에 발표하십시오."
      ]
    }',
    CURRENT_TIMESTAMP(6)
FROM (
         SELECT company_id
         FROM company_aliases
         WHERE normalized_alias = '기업은행'

         UNION ALL

         SELECT id
         FROM companies
         WHERE normalized_name IN (
                                   '기업은행',
                                   'ibk기업은행'
             )

             LIMIT 1
     ) chosen;