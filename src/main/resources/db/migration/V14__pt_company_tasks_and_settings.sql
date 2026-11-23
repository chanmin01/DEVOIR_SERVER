-- 기존 과제에는 기존 진행 방식과 같은 기본값을 적용합니다.

ALTER TABLE pt_tasks ADD COLUMN task_type VARCHAR(30)
    NOT NULL DEFAULT 'SERVICE_IMPROVEMENT';

ALTER TABLE pt_tasks ADD COLUMN target_job_role VARCHAR(100) NULL;

ALTER TABLE pt_tasks ADD COLUMN allowed_tracks VARCHAR(100)
    NOT NULL DEFAULT 'SECURITY,UI_UX,AI,DATA';

ALTER TABLE pt_tasks ADD COLUMN preparation_minutes_options VARCHAR(100)
    NOT NULL DEFAULT '15,20';

ALTER TABLE pt_tasks ADD COLUMN presentation_seconds INT
    NOT NULL DEFAULT 300;


-- 기존 회사가 있으면 재사용합니다.
-- DB마다 다른 회사 ID를 고정하지 않습니다.

INSERT IGNORE INTO companies (
    name,
    normalized_name,
    source,
    created_at
)
VALUES
    (
        '현대자동차',
        '현대자동차',
        'USER',
        CURRENT_TIMESTAMP(6)
    ),
    (
        '삼성전자',
        '삼성전자',
        'USER',
        CURRENT_TIMESTAMP(6)
    ),
    (
        'SK하이닉스',
        'sk하이닉스',
        'USER',
        CURRENT_TIMESTAMP(6)
    );


-- 현대자동차 IT·디지털 직무 연습용 과제

INSERT INTO pt_tasks (
    company_id,
    company_type,
    code,
    task_version,
    status,
    content_json,
    created_at,
    task_type,
    target_job_role,
    allowed_tracks,
    preparation_minutes_options,
    presentation_seconds
)
SELECT
    id,
    'BIG_TECH_SW',
    'HYUNDAI_CONNECTED_CONTROL_DEMO_V1',
    1,
    'APPROVED',
    '{
      "title": "커넥티드카 원격 제어의 처리 지연·상태 불일치 개선",
      "serviceDescription": "현대자동차 IT·디지털 직무를 연습하기 위한 가상 서비스입니다. 운전자가 모바일 앱에서 차량 문 잠금이나 공조 작동을 요청하면 서버가 명령을 전달하고 차량의 처리 결과를 앱에 표시합니다. 실제 제품의 내부 구조를 설명하는 자료는 아닙니다.",
      "currentFlow": [
        "앱에서 차량 선택",
        "원격 제어 요청",
        "서버 명령 접수",
        "차량 명령 처리",
        "처리 결과 수신",
        "앱 상태 갱신"
      ],
      "problemSituation": "가상 서비스에서 사용자는 앱이 응답하지 않으면 같은 명령을 반복 요청합니다. 차량에서는 명령이 수행됐지만 앱에는 대기 중으로 남는 경우도 있습니다. 처리 지연과 상태 불일치를 줄이되 성공하지 않은 명령을 성공으로 표시해서는 안 됩니다.",
      "availableData": [
        "하루 원격 제어 요청 10만 건, 사용자 재시도 비율 12%라는 연습용 집계",
        "정상 요청의 95백분위 완료 시간 18초, 차량 오프라인 비율 4%라는 연습용 집계",
        "요청 ID·차량 식별자·접수 시각·전달 시각·결과 수신 시각이 담긴 가상 로그",
        "로그 일부는 차량 응답이 누락되어 실제 실행 여부를 즉시 확정할 수 없음"
      ],
      "constraints": [
        "백엔드 개발자 3명과 앱 개발자 1명, 개선 기간 6주",
        "차량 펌웨어는 이번 개선 범위에서 변경할 수 없음",
        "기존 앱 버전과 호환되어야 하며 차량 명령의 중복 실행 위험을 고려해야 함",
        "차량 식별정보를 불필요하게 로그에 노출하지 않아야 함"
      ],
      "exerciseAssumptions": [
        "본 과제는 DEVOIR가 직접 제작한 연습용 가상 과제이며 실제 기출이 아닙니다.",
        "제시된 수치·장애·인력·기간은 모두 연습용 가정이며 해당 기업의 실제 내부 상황을 뜻하지 않습니다.",
        "준비 15분 또는 20분, 발표 5분은 DEVOIR 연습 설정이며 공식 채용 전형의 시간을 뜻하지 않습니다."
      ],
      "trackGuidance": {},
      "presentationRequirements": [
        "분야를 따로 선택하지 말고 지정된 직무 문제를 해결하십시오.",
        "핵심 문제와 우선순위, 추가로 확인할 정보를 설명하십시오.",
        "원인 가설과 대안 두 가지 이상을 비교하고 선택 근거를 제시하십시오.",
        "실행 순서, 실패 시 대응, 위험과 트레이드오프를 설명하십시오.",
        "기대 효과를 어떤 지표와 실험으로 검증할지 제시하십시오.",
        "사실과 가정을 구분하여 5분 이내로 발표하십시오."
      ]
    }',
    CURRENT_TIMESTAMP(6),
    'JOB_PROBLEM_SOLVING',
    'IT·디지털 / 백엔드',
    '',
    '15,20',
    300
FROM companies
WHERE normalized_name = '현대자동차';


-- 삼성전자 IT·소프트웨어 직무 연습용 과제

INSERT INTO pt_tasks (
    company_id,
    company_type,
    code,
    task_version,
    status,
    content_json,
    created_at,
    task_type,
    target_job_role,
    allowed_tracks,
    preparation_minutes_options,
    presentation_seconds
)
SELECT
    id,
    'BIG_TECH_SW',
    'SAMSUNG_SMARTHOME_SYNC_DEMO_V1',
    1,
    'APPROVED',
    '{
      "title": "스마트홈 기기 상태 동기화와 자동화 신뢰성 개선",
      "serviceDescription": "삼성전자 IT·소프트웨어 직무를 연습하기 위한 가상 스마트홈 플랫폼입니다. 여러 기기의 상태를 앱에 표시하고 센서 조건에 따라 자동화 규칙을 실행합니다. 실제 삼성전자 제품의 구조나 장애를 재현한 과제가 아닙니다.",
      "currentFlow": [
        "기기 상태 이벤트 전송",
        "플랫폼 이벤트 수신",
        "상태 저장",
        "자동화 조건 판단",
        "기기 제어 요청",
        "앱 상태 표시"
      ],
      "problemSituation": "네트워크 재연결 때 늦은 이벤트와 중복 이벤트가 유입됩니다. 앱에 과거 상태가 표시되거나 자동화가 반복 실행되는 현상이 발생한다고 가정합니다. 최신 상태를 신뢰할 수 있게 표시하고 자동화의 잘못된 실행을 줄이는 방안을 제시하십시오.",
      "availableData": [
        "일일 상태 이벤트 100만 건, 중복 이벤트 추정 비율 3%라는 가상 집계",
        "재연결 기기에서 최대 90초 늦은 이벤트가 관찰된다는 가정",
        "기기 시각과 서버 수신 시각은 있으나 일부 기기 시계는 정확하지 않음",
        "가정별 기기 수·이벤트 순서·자동화 실행 이력의 가상 표본"
      ],
      "constraints": [
        "플랫폼 개발자 4명, 개선 기간 8주",
        "기존 기기 전체의 소프트웨어를 일괄 갱신할 수 없음",
        "응답 속도·데이터 정합성·운영 비용 사이의 균형을 설명해야 함",
        "서비스 중단 없이 점진적으로 적용해야 함"
      ],
      "exerciseAssumptions": [
        "본 과제는 DEVOIR가 직접 제작한 연습용 가상 과제이며 실제 기출이 아닙니다.",
        "제시된 수치·장애·인력·기간은 모두 연습용 가정이며 해당 기업의 실제 내부 상황을 뜻하지 않습니다.",
        "준비 15분 또는 20분, 발표 5분은 DEVOIR 연습 설정이며 공식 채용 전형의 시간을 뜻하지 않습니다."
      ],
      "trackGuidance": {},
      "presentationRequirements": [
        "분야를 따로 선택하지 말고 지정된 직무 문제를 해결하십시오.",
        "핵심 문제와 우선순위, 추가로 확인할 정보를 설명하십시오.",
        "원인 가설과 대안 두 가지 이상을 비교하고 선택 근거를 제시하십시오.",
        "실행 순서, 실패 시 대응, 위험과 트레이드오프를 설명하십시오.",
        "기대 효과를 어떤 지표와 실험으로 검증할지 제시하십시오.",
        "사실과 가정을 구분하여 5분 이내로 발표하십시오."
      ]
    }',
    CURRENT_TIMESTAMP(6),
    'JOB_PROBLEM_SOLVING',
    'IT·소프트웨어 / 플랫폼',
    '',
    '15,20',
    300
FROM companies
WHERE normalized_name = '삼성전자';


-- SK하이닉스 IT·디지털 직무 연습용 과제

INSERT INTO pt_tasks (
    company_id,
    company_type,
    code,
    task_version,
    status,
    content_json,
    created_at,
    task_type,
    target_job_role,
    allowed_tracks,
    preparation_minutes_options,
    presentation_seconds
)
SELECT
    id,
    'BIG_TECH_SW',
    'SKHYNIX_ANOMALY_MONITORING_DEMO_V1',
    1,
    'APPROVED',
    '{
      "title": "제조 설비 이상징후 모니터링의 오탐·탐지 지연 개선",
      "serviceDescription": "SK하이닉스 IT·디지털 직무를 연습하기 위한 가상 제조 설비 모니터링 시스템입니다. 센서와 공정 로그를 수집하여 이상징후를 알리고 담당자가 점검 여부를 판단합니다. 실제 반도체 공정 조건이나 기업 내부 데이터를 제공하는 과제는 아닙니다.",
      "currentFlow": [
        "센서·공정 로그 수집",
        "데이터 정제",
        "이상징후 탐지",
        "알림 발송",
        "담당자 확인",
        "점검 결과 기록"
      ],
      "problemSituation": "설비 운전 조건이 바뀌면 경보가 집중되고 일부 센서 결측 때문에 탐지가 늦어집니다. 운영자는 알림을 모두 확인하기 어렵습니다. 중요한 이상을 놓치지 않으면서 오탐과 확인 부담을 줄이는 개선안을 제시하십시오.",
      "availableData": [
        "하루 경보 200건 중 담당자가 불필요하다고 기록한 경보 60%라는 가상 집계",
        "센서 결측률 5%, 탐지 지연 중앙값 4분이라는 가상 집계",
        "설비별 센서 시계열·운전 조건·경보·점검 이력의 가상 표본",
        "점검 결과 라벨은 일부만 존재하며 불필요 경보가 곧 정상이라는 뜻은 아님"
      ],
      "constraints": [
        "데이터·플랫폼 담당자 3명, 개선 기간 8주",
        "생산 설비를 직접 제어하거나 자동 정지하는 기능은 범위에서 제외",
        "새 센서를 구매하지 않고 기존 수집 데이터를 활용",
        "오탐 감소와 미탐 증가의 상충관계를 검토하고 현장 검증 절차를 제시해야 함"
      ],
      "exerciseAssumptions": [
        "본 과제는 DEVOIR가 직접 제작한 연습용 가상 과제이며 실제 기출이 아닙니다.",
        "제시된 수치·장애·인력·기간은 모두 연습용 가정이며 해당 기업의 실제 내부 상황을 뜻하지 않습니다.",
        "준비 15분 또는 20분, 발표 5분은 DEVOIR 연습 설정이며 공식 채용 전형의 시간을 뜻하지 않습니다."
      ],
      "trackGuidance": {},
      "presentationRequirements": [
        "분야를 따로 선택하지 말고 지정된 직무 문제를 해결하십시오.",
        "핵심 문제와 우선순위, 추가로 확인할 정보를 설명하십시오.",
        "원인 가설과 대안 두 가지 이상을 비교하고 선택 근거를 제시하십시오.",
        "실행 순서, 실패 시 대응, 위험과 트레이드오프를 설명하십시오.",
        "기대 효과를 어떤 지표와 실험으로 검증할지 제시하십시오.",
        "사실과 가정을 구분하여 5분 이내로 발표하십시오."
      ]
    }',
    CURRENT_TIMESTAMP(6),
    'JOB_PROBLEM_SOLVING',
    'IT·디지털 / 데이터 플랫폼',
    '',
    '15,20',
    300
FROM companies
WHERE normalized_name = 'sk하이닉스';