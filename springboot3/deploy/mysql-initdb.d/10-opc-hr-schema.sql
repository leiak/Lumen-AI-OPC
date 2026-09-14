-- ============================================================
-- opc-hr schema (W71 · opc-hr 9322)
-- 6 表：job / candidate / application / interview / offer / match_score
-- 镜像自 V20260911__opc_hr_schema.sql，initdb 用 IF NOT EXISTS 模式
-- ============================================================

CREATE TABLE IF NOT EXISTS opc_hr_job (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  created_by      BIGINT NOT NULL,
  title           VARCHAR(128) NOT NULL,
  category        VARCHAR(32) NOT NULL,
  description     TEXT NOT NULL,
  full_jd         TEXT,
  skills_json     JSON,
  salary_min      DECIMAL(12,2),
  salary_max      DECIMAL(12,2),
  location        VARCHAR(64),
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  publish_at      DATETIME,
  close_at        DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status (company_id, status),
  UNIQUE KEY uk_company_title (company_id, title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招聘需求 (JD)';

CREATE TABLE IF NOT EXISTS opc_hr_candidate (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(64) NOT NULL,
  email           VARCHAR(128),
  phone           VARCHAR(32),
  resume_url      VARCHAR(512) NOT NULL,
  resume_md       MEDIUMTEXT,
  parsed_json     JSON,
  embedding       BLOB,
  source          VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  tags_json       JSON,
  created_by      BIGINT NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_name (company_id, name),
  UNIQUE KEY uk_company_email (company_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候选人';

CREATE TABLE IF NOT EXISTS opc_hr_application (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  job_id          BIGINT NOT NULL,
  candidate_id    BIGINT NOT NULL,
  channel         VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  score           INT NOT NULL DEFAULT 0,
  score_reason    TEXT,
  status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
  current_stage   VARCHAR(32),
  applied_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_job_status (company_id, job_id, status),
  UNIQUE KEY uk_job_candidate (job_id, candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候选人投递';

CREATE TABLE IF NOT EXISTS opc_hr_interview (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  application_id  BIGINT NOT NULL,
  round           INT NOT NULL,
  type            VARCHAR(16) NOT NULL,
  interviewer_id  BIGINT NOT NULL,
  scheduled_at    DATETIME NOT NULL,
  duration_min    INT NOT NULL DEFAULT 60,
  feedback        TEXT,
  result          VARCHAR(16),
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_company_app (company_id, application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试安排';

CREATE TABLE IF NOT EXISTS opc_hr_offer (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  application_id  BIGINT NOT NULL,
  salary          DECIMAL(12,2) NOT NULL,
  start_date      DATE NOT NULL,
  expire_at       DATETIME NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  sent_at         DATETIME,
  responded_at    DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Offer';

CREATE TABLE IF NOT EXISTS opc_hr_match_score (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  job_id          BIGINT NOT NULL,
  candidate_id    BIGINT NOT NULL,
  score           INT NOT NULL,
  reason          TEXT,
  computed_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_candidate (job_id, candidate_id),
  KEY idx_company_job (company_id, job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 简历匹配打分';