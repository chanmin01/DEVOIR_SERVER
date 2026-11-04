-- MySQL dump 10.13  Distrib 8.0.46, for Linux (aarch64)
--
-- Host: localhost    Database: benly
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `answers`
--

DROP TABLE IF EXISTS `answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `duration_sec` int DEFAULT NULL,
  `input_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stt_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transcript` text COLLATE utf8mb4_unicode_ci,
  `question_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKa8hukb7yjph7wnrlnn8x0fcja` (`question_id`),
  CONSTRAINT `FK3erw1a3t0r78st8ty27x6v3g1` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `answers`
--

LOCK TABLES `answers` WRITE;
/*!40000 ALTER TABLE `answers` DISABLE KEYS */;
INSERT INTO `answers` VALUES (1,'2026-08-03 21:38:53.613445',NULL,'TEXT',NULL,'재고 차감 로직에서 동시 요청이 몰리면서 재고가 음수로 떨어지는 문제가 있었습니다. 낙관적 락으로 해결했습니다.',6),(2,'2026-08-03 21:44:10.670682',NULL,'TEXT',NULL,'재고 차감 로직에서 동시 요청이 몰리면서 재고가 음수로 떨어지는 문제가 있었습니다. 낙관적 락으로 해결했습니다.',7),(3,'2026-08-04 17:07:44.290412',NULL,'TEXT',NULL,'Controller는 HTTP 요청/응답 처리와 검증만 담당하고, Service에 비즈니스 로직을 위임했습니다. Service는 트랜잭션 단위로 도메인 로직을 처리하고, Repository는 DB 접근만 담당하도록 분리했습니다. 예를 들어 세션 생성 시 Controller는 요청 검증만 하고, Service에서 유저 조회와 세션 저장을 트랜잭션으로 묶었습니다.',11),(4,'2026-08-04 17:13:40.586856',NULL,'TEXT',NULL,'목록 조회 시 연관 엔티티를 개별 쿼리로 가져오면서 N+1이 발생했습니다. Hibernate 로그로 쿼리가 여러 번 나가는 걸 발견했고, fetch join과 @EntityGraph로 한 번에 조회하도록 해결했습니다. 배치 사이즈 설정도 병행했습니다.',12),(5,'2026-08-04 17:14:29.287877',NULL,'TEXT',NULL,'여기에 꼬리질문 16에 맞는 답변...',16),(6,'2026-08-04 17:16:00.460569',NULL,'TEXT',NULL,'여기에 꼬리질문 16에 맞는 답변...',17),(7,'2026-08-04 23:35:34.680091',NULL,'AUDIO','COMPLETED','네, 조회 성능이 느려지는 문제를 겪은 적이 있습니다. 사용자가 늘면서 특정 목록 조회 API의 응답 시간이 길어졌는데, Slow Query 로그를 확인해보니 인덱스를 타지 않는 쿼리가 원인이었습니다. 실행 계획을 익스플레인으로 분석해서 풀 테이블 스캔이 발생하는 걸 확인하였고, 외열 조건에 맞는 복합 인덱스를 추가해서 해결했습니다. 락 관련해서는 동시에 같은 행을 업데이트하는 요청이 몰리면서 락 대기가 길어지는 문제가 있었습니다. 트랜잭션 범위를 최소화하고 필요한 경우 낙관적 락으로 전환해서 락 경합을 줄였습니다. 커넥션 풀은 하이카르 PCP를 사용했는데, 트래픽이 몰릴 때 커넥션이 부족해지는 현상이 있어서 풀 사이즈를 모니터링하면서 적절히 조정했습니다.',13),(8,'2026-08-06 14:15:39.660531',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',18),(9,'2026-08-06 14:15:50.052790',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',19),(10,'2026-08-06 14:15:57.025790',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',14),(11,'2026-08-06 14:16:06.129235',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',20),(12,'2026-08-06 14:16:14.038115',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',21),(13,'2026-08-06 14:16:20.829252',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',15),(14,'2026-08-06 14:16:28.378855',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',22),(15,'2026-08-06 14:16:35.067692',NULL,'TEXT',NULL,'꼬리2 질문에 맞는 답변...',23),(16,'2026-08-07 14:56:27.291199',NULL,'SKIP',NULL,NULL,24),(17,'2026-08-07 14:57:14.664922',NULL,'TEXT',NULL,'재고 차감 로직에서 동시 요청이...',25),(18,'2026-08-07 14:58:06.398060',NULL,'SKIP',NULL,NULL,29),(19,'2026-10-19 13:39:26.967393',NULL,'TEXT',NULL,'저는 Redis 분산 락으로 동시성 문제를 해결했습니다. 재고 차감에서 중복 처리가 발생해 Lettuce 기반 스핀락을 먼저 적용했는데 CPU 부하가 커서 Redisson의 pub/sub 방식으로 바꿨습니다.',36),(20,'2026-10-19 16:24:06.601275',NULL,'TEXT',NULL,'자바와 스프링이 제게 가장 잘 맞는 것 같습니다. 이를 사용해서 스터디를 진행한 경험도 있으며 의존성주입 및 객체지향적인 사고를 가지고 스프링 빈 등의 개념을 사용해서 설계를 하고 프로그래밍 한 경험이 있습니다.',42),(21,'2026-10-19 16:27:13.023376',NULL,'TEXT',NULL,'생성자 주입을 사용하는 것이 좋다고 알고 있습니다 di로서 생성될 때로 제한을 두어서 외부에서 어떤 상황이 필요하다고 했을 때 그거에 맞는 것을 스프링 컨테이너가 알아서 판단을 하고 동적으로 생성하는 것으로 알고 있습니다.',47),(22,'2026-10-19 16:52:25.785641',NULL,'TEXT',NULL,'규칙에 맞게 각 도메인에 맞게 프론트와 백이 소통할 수 있는 창구라고 생각합니다. 프로젝트를 하면서 제 도메인에 맞는 부분을 직접 작성해본 경험이 있습니다.',48),(23,'2026-10-19 16:53:38.107109',NULL,'TEXT',NULL,'post, get, put, delete를 적절하게 사용하였고 id나 식별이 필요한 부분은 눈에 띄게 확인할 수 있도록 반영했습니다.',53),(24,'2026-10-19 16:54:25.864870',NULL,'TEXT',NULL,'userId로 한번 특정 주문 목록 id로 한번 이렇게 두 층위로 나누어서 작성할 것 같습니다.',54);
/*!40000 ALTER TABLE `answers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `archive_likes`
--

DROP TABLE IF EXISTS `archive_likes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `archive_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_like_user_question` (`user_id`,`question_id`),
  KEY `fk_like_question` (`question_id`),
  CONSTRAINT `FK9gtqgrk6iegneyly3vnii5gcr` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_like_question` FOREIGN KEY (`question_id`) REFERENCES `archive_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `archive_likes`
--

LOCK TABLES `archive_likes` WRITE;
/*!40000 ALTER TABLE `archive_likes` DISABLE KEYS */;
/*!40000 ALTER TABLE `archive_likes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `archive_questions`
--

DROP TABLE IF EXISTS `archive_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `archive_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `archive_id` bigint NOT NULL,
  `seq` int NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_question_archive` (`archive_id`),
  CONSTRAINT `fk_question_archive` FOREIGN KEY (`archive_id`) REFERENCES `interview_archives` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `archive_questions`
--

LOCK TABLES `archive_questions` WRITE;
/*!40000 ALTER TABLE `archive_questions` DISABLE KEYS */;
/*!40000 ALTER TABLE `archive_questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `archive_reports`
--

DROP TABLE IF EXISTS `archive_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `archive_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `archive_id` bigint NOT NULL,
  `reason` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_user_archive` (`user_id`,`archive_id`),
  KEY `fk_report_archive` (`archive_id`),
  CONSTRAINT `FK4blqn9d9b8c4lj9wb1hwmlyy5` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_report_archive` FOREIGN KEY (`archive_id`) REFERENCES `interview_archives` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `archive_reports`
--

LOCK TABLES `archive_reports` WRITE;
/*!40000 ALTER TABLE `archive_reports` DISABLE KEYS */;
/*!40000 ALTER TABLE `archive_reports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `companies`
--

DROP TABLE IF EXISTS `companies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `corp_code` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_normalized_name` (`normalized_name`),
  UNIQUE KEY `uk_company_corp_code` (`corp_code`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `companies`
--

LOCK TABLES `companies` WRITE;
/*!40000 ALTER TABLE `companies` DISABLE KEYS */;
INSERT INTO `companies` VALUES (1,NULL,'삼성전자','삼성전자','DART','2026-11-03 19:27:01.496594'),(2,NULL,'SK하이닉스','sk하이닉스','DART','2026-11-03 19:27:01.496594'),(3,NULL,'LG CNS','lgcns','DART','2026-11-03 19:27:01.496594'),(4,NULL,'네이버','네이버','DART','2026-11-03 19:27:01.496594'),(5,NULL,'카카오','카카오','DART','2026-11-03 19:27:01.496594'),(6,NULL,'쿠팡','쿠팡','DART','2026-11-03 19:27:01.496594'),(7,NULL,'우아한형제들','우아한형제들','DART','2026-11-03 19:27:01.496594'),(8,NULL,'라인플러스','라인플러스','DART','2026-11-03 19:27:01.496594'),(9,NULL,'비바리퍼블리카','비바리퍼블리카','DART','2026-11-03 19:27:01.496594'),(10,NULL,'카카오뱅크','카카오뱅크','DART','2026-11-03 19:27:01.496594'),(11,NULL,'카카오페이','카카오페이','DART','2026-11-03 19:27:01.496594'),(12,NULL,'신한은행','신한은행','DART','2026-11-03 19:27:01.496594'),(13,NULL,'KB국민은행','kb국민은행','DART','2026-11-03 19:27:01.496594'),(14,NULL,'기업은행','기업은행','USER','2026-11-03 19:27:01.738637');
/*!40000 ALTER TABLE `companies` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `company_aliases`
--

DROP TABLE IF EXISTS `company_aliases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_aliases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `alias` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_alias` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias_normalized` (`normalized_alias`),
  KEY `fk_alias_company` (`company_id`),
  CONSTRAINT `fk_alias_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `company_aliases`
--

LOCK TABLES `company_aliases` WRITE;
/*!40000 ALTER TABLE `company_aliases` DISABLE KEYS */;
INSERT INTO `company_aliases` VALUES (1,5,'kakao','kakao'),(2,4,'naver','naver'),(3,6,'coupang','coupang'),(4,7,'배민','배민'),(5,7,'배달의민족','배달의민족'),(6,9,'토스','토스'),(7,9,'toss','toss'),(8,1,'삼성','삼성'),(9,2,'하이닉스','하이닉스'),(10,8,'라인','라인'),(11,13,'국민은행','국민은행');
/*!40000 ALTER TABLE `company_aliases` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `documents`
--

DROP TABLE IF EXISTS `documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `storage_key` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkxttj4tp5le2uth212lu49vny` (`user_id`),
  CONSTRAINT `FKkxttj4tp5le2uth212lu49vny` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `documents`
--

LOCK TABLES `documents` WRITE;
/*!40000 ALTER TABLE `documents` DISABLE KEYS */;
/*!40000 ALTER TABLE `documents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedbacks`
--

DROP TABLE IF EXISTS `feedbacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feedbacks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` json DEFAULT NULL,
  `topic_score` int DEFAULT NULL,
  `question_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgw5ufpae15h4v065i8sl4mp6x` (`question_id`),
  CONSTRAINT `FK4mh5e1gcrwa2wo12sakpgkg7e` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedbacks`
--

LOCK TABLES `feedbacks` WRITE;
/*!40000 ALTER TABLE `feedbacks` DISABLE KEYS */;
/*!40000 ALTER TABLE `feedbacks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'0','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'root','2026-10-28 10:57:54',0,1),(2,'1','guest session and followup strategy','SQL','V1__guest_session_and_followup_strategy.sql',393398594,'root','2026-10-28 10:57:55',101,1),(3,'2','create archive tables and seed companies','SQL','V2__create_archive_tables_and_seed_companies.sql',-682506676,'root','2026-11-03 10:27:01',164,1),(4,'3','move archive status to archive','SQL','V3__move_archive_status_to_archive.sql',-1926497315,'root','2026-11-03 10:27:01',175,1),(5,'4','pt task and session','SQL','V4__pt_task_and_session.sql',1748175138,'root','2026-11-03 10:27:01',38,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `interview_archives`
--

DROP TABLE IF EXISTS `interview_archives`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `interview_archives` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `company_id` bigint NOT NULL,
  `company_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stage` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `format` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_role` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `interviewed_at` date NOT NULL,
  `memo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'VISIBLE',
  `like_count` int NOT NULL DEFAULT '0',
  `report_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_archive_company_interviewed` (`company_id`,`interviewed_at`),
  KEY `idx_archive_visible_latest` (`status`,`deleted_at`,`id`),
  KEY `idx_archive_visible_popular` (`status`,`deleted_at`,`like_count`,`id`),
  KEY `idx_archive_company_visible` (`company_id`,`status`,`id`),
  KEY `FKihi02pfdmlfgi4d2x0opixvwy` (`user_id`),
  CONSTRAINT `fk_archive_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKihi02pfdmlfgi4d2x0opixvwy` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `interview_archives`
--

LOCK TABLES `interview_archives` WRITE;
/*!40000 ALTER TABLE `interview_archives` DISABLE KEYS */;
/*!40000 ALTER TABLE `interview_archives` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_sessions`
--

DROP TABLE IF EXISTS `pt_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_sessions` (
  `session_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `creation_request_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phase` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `preparation_minutes` int NOT NULL,
  `preparation_started_at` datetime(6) NOT NULL,
  `preparation_deadline` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_pt_creation_request` (`creation_request_id`),
  KEY `fk_pt_detail_task` (`task_id`),
  KEY `ix_pt_detail_expiry` (`expires_at`),
  CONSTRAINT `FK5truikw8yu9o36vkvor369t01` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`),
  CONSTRAINT `fk_pt_detail_task` FOREIGN KEY (`task_id`) REFERENCES `pt_tasks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_sessions`
--

LOCK TABLES `pt_sessions` WRITE;
/*!40000 ALTER TABLE `pt_sessions` DISABLE KEYS */;
/*!40000 ALTER TABLE `pt_sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pt_tasks`
--

DROP TABLE IF EXISTS `pt_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pt_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `company_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_version` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_json` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pt_task_code` (`company_id`,`code`),
  KEY `ix_pt_task_company_status` (`company_id`,`status`,`id`),
  CONSTRAINT `fk_pt_task_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pt_tasks`
--

LOCK TABLES `pt_tasks` WRITE;
/*!40000 ALTER TABLE `pt_tasks` DISABLE KEYS */;
INSERT INTO `pt_tasks` VALUES (1,14,'FINANCE_IT','SME_SERVICE_V1',1,'APPROVED','{\n      \"title\": \"중소기업 위험 진단·금융상품 추천 서비스 고도화\",\n      \"serviceDescription\": \"기업은행을 대상으로 연습하는 가상 서비스입니다. 중소기업 대표가 기업 정보를 제출하면 재무·거래 자료로 위험 요인을 확인하고 금융상품 후보를 추천받습니다. 담당자는 상담 보조자료로 사용하며 최종 대출 승인을 자동 결정하지 않습니다. 실제 기업은행 서비스나 기출문제에 관한 사실 진술이 아닙니다.\",\n      \"currentFlow\": [\n        \"기업정보 입력\",\n        \"자료 제공 동의\",\n        \"재무·거래 정보 조회\",\n        \"위험 진단\",\n        \"추천상품 확인\",\n        \"상담 신청\"\n      ],\n      \"problemSituation\": \"가상 연습 상황에서 자료 제공 동의 중 이탈, 추천 이유 이해의 어려움, 결측 자료의 신뢰도 판단 문제, 담당자의 재확인 시간이 발생합니다. 우선 해결할 문제를 정하십시오.\",\n      \"availableData\": [\n        \"동의받아 수집한 재무정보\",\n        \"거래 요약정보\",\n        \"서비스 이용 로그\",\n        \"상담 기록\"\n      ],\n      \"constraints\": [\n        \"기존 상담 절차와 연결해야 합니다.\",\n        \"새로운 외부 데이터 구매 없이 개선해야 합니다.\",\n        \"보유 자료마다 최신성과 결측 수준이 다를 수 있습니다.\"\n      ],\n      \"exerciseAssumptions\": [\n        \"개발 인력 4명, 개발 기간 3개월은 연습용 가정입니다.\",\n        \"서비스와 문제 상황은 연습용으로 구성했으며 실제 기업의 확인된 문제를 뜻하지 않습니다.\"\n      ],\n      \"trackGuidance\": {\n        \"SECURITY\": \"정보 접근·동의·오남용 방지 과정의 개선안을 제시하십시오.\",\n        \"UI_UX\": \"입력·동의·결과 이해·상담 연결 과정의 개선안을 제시하십시오.\",\n        \"AI\": \"진단·추천·설명 품질 개선안을 제시하십시오.\",\n        \"DATA\": \"자료 품질·최신성·결측·검증 체계의 개선안을 제시하십시오.\"\n      },\n      \"presentationRequirements\": [\n        \"네 분야 중 하나를 선택하십시오.\",\n        \"핵심 문제와 우선순위의 근거를 설명하십시오.\",\n        \"대안 비교와 선택 이유를 설명하십시오.\",\n        \"실행 순서와 위험을 설명하십시오.\",\n        \"기대 효과의 검증 방법을 설명하십시오.\",\n        \"제공되지 않은 수치는 가정임을 밝혀 5분 안에 발표하십시오.\"\n      ]\n    }','2026-11-03 19:27:01.739824');
/*!40000 ALTER TABLE `pt_tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `questions`
--

DROP TABLE IF EXISTS `questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `seq` int NOT NULL,
  `short_title` text COLLATE utf8mb4_unicode_ci,
  `strategy` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `source_type` enum('CLAUDE','SEED_FALLBACK') COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8auh7sl8qhf8w4b0lk4q2xpo5` (`parent_id`),
  KEY `FK1b698bj111khhll6tcdjl52di` (`session_id`),
  CONSTRAINT `FK1b698bj111khhll6tcdjl52di` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`),
  CONSTRAINT `FK8auh7sl8qhf8w4b0lk4q2xpo5` FOREIGN KEY (`parent_id`) REFERENCES `questions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `questions`
--

LOCK TABLES `questions` WRITE;
/*!40000 ALTER TABLE `questions` DISABLE KEYS */;
INSERT INTO `questions` VALUES (1,'2026-07-31 18:55:25.251216','최근 진행한 프로젝트에서 기술적으로 가장 어려웠던 문제와 해결 과정을 설명해주세요.',1,NULL,NULL,'MAIN',NULL,'SEED_FALLBACK',3),(2,'2026-07-31 18:55:25.254506','RESTful API를 설계할 때 중요하게 고려하는 원칙은 무엇인가요?',2,NULL,NULL,'MAIN',NULL,'SEED_FALLBACK',3),(3,'2026-07-31 18:55:25.256514','데이터베이스 인덱스가 무엇이고, 언제 사용하면 좋은지 설명해주세요.',3,NULL,NULL,'MAIN',NULL,'SEED_FALLBACK',3),(4,'2026-07-31 18:55:25.258056','대용량 트래픽을 처리하기 위한 방법을 아는 대로 설명해주세요.',4,NULL,NULL,'MAIN',NULL,'SEED_FALLBACK',3),(5,'2026-07-31 18:55:25.259545','캐시(Cache)를 사용하는 이유와 발생할 수 있는 문제점은 무엇인가요?',5,NULL,NULL,'MAIN',NULL,'SEED_FALLBACK',3),(6,'2026-07-31 21:29:47.919653','대용량 트래픽 처리 경험에 대해 구체적으로 설명해주세요. 어떤 상황이었고, 어느 정도의 트래픽 규모(TPS, 동시접속자 수 등)를 처리하셨나요?',1,NULL,NULL,'MAIN',NULL,'CLAUDE',4),(7,'2026-07-31 21:29:47.937352','트래픽 급증 상황에서 시스템 병목 지점을 어떻게 파악하고 해결하셨는지 구체적인 사례를 들어 설명해주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',4),(8,'2026-07-31 21:29:47.939406','대용량 트래픽 환경에서 데이터베이스 부하를 줄이기 위해 어떤 캐싱 전략이나 DB 최적화 기법을 적용해보셨나요?',3,NULL,NULL,'MAIN',NULL,'CLAUDE',4),(9,'2026-07-31 21:29:47.942986','서버 확장(Scale-out/Scale-up)을 고려할 때, 무상태(Stateless) 설계와 세션 관리를 어떻게 처리하시는지 설명해주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',4),(10,'2026-07-31 21:29:47.945105','장애 발생 시 서비스 다운타임을 최소화하기 위해 적용했던 장애 대응 및 모니터링 전략(예: Circuit Breaker, 오토스케일링, 알림 시스템 등)에 대해 말씀해주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',4),(11,'2026-08-04 17:04:25.995760','Spring Boot 기반 프로젝트에서 계층형 아키텍처(Controller-Service-Repository)를 설계할 때 각 계층의 책임을 어떻게 분리하셨는지 구체적인 예시와 함께 설명해 주세요.',1,NULL,NULL,'MAIN',NULL,'CLAUDE',5),(12,'2026-08-04 17:04:26.007061','JPA를 사용하면서 N+1 문제를 겪은 경험이 있다면, 문제를 어떻게 발견하고 해결했는지 설명해 주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',5),(13,'2026-08-04 17:04:26.010780','MySQL에서 대용량 트래픽 처리 시 발생할 수 있는 성능 이슈(예: 인덱스, 락, 커넥션 풀)를 경험하신 적이 있다면, 어떤 방식으로 진단하고 개선했는지 말씀해 주세요.',3,NULL,NULL,'MAIN',NULL,'CLAUDE',5),(14,'2026-08-04 17:04:26.013737','AWS 환경에서 백엔드 서비스를 운영하며 오토스케일링, 로드밸런싱 등을 활용해 트래픽 급증에 대응한 경험이 있다면 구체적으로 설명해 주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',5),(15,'2026-08-04 17:04:26.016515','대용량 트래픽 상황에서 서비스의 응답 속도를 개선하기 위해 캐싱 전략(예: Redis)이나 비동기 처리를 도입한 경험이 있다면, 도입 배경과 효과를 설명해 주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',5),(16,'2026-08-04 17:13:44.933749','fetch join과 @EntityGraph를 각각 어떤 상황에 선택적으로 적용하셨나요? 예를 들어 컬렉션이 여러 개 연관된 경우(컬렉션 fetch join 중복 문제)나 페이징이 필요한 조회에서는 각각 어떤 한계가 있었고, 그때는 어떻게 대응하셨는지 구체적으로 설명해 주시겠어요?',1,NULL,NULL,'FOLLOW_UP',12,'CLAUDE',5),(17,'2026-08-04 17:14:36.091702','답변하신 내용이 다소 형식적으로 느껴지는데, 실제로 겪었던 구체적인 사례를 들어 설명해 주시겠어요? 예를 들어 컬렉션 두 개 이상을 fetch join 했을 때 발생한 카테시안 곱(중복 데이터) 문제를 직접 경험하셨다면, 그 상황에서 배치 사이즈(hibernate.default_batch_fetch_size)를 얼마로 설정했고, 왜 그 값을 선택했는지, 그리고 페이징이 필요한 쿼리에서는 fetch join 대신 어떤 방식(예: 별도 조회, DTO 프로젝션 등)으로 해결하셨는지 구체적인 수치나 코드 흐름과 함께 말씀해 주세요.',2,NULL,NULL,'FOLLOW_UP',12,'CLAUDE',5),(18,'2026-08-04 23:35:39.635517','복합 인덱스를 추가하실 때 어떤 컬럼들을 어떤 순서로 구성하셨는지, 그리고 그 순서를 그렇게 정한 이유(카디널리티, 쿼리 패턴 등)를 설명해 주시겠어요? 또한 낙관적 락으로 전환하면서 발생할 수 있는 충돌(재시도 로직 등)은 어떻게 처리하셨나요?',1,NULL,NULL,'FOLLOW_UP',13,'CLAUDE',5),(19,'2026-08-06 14:15:43.604553','커넥션 풀(HikariCP) 사이즈를 조정하실 때, 적정 풀 사이즈를 어떤 기준과 지표(예: DB 서버의 max_connections, 애플리케이션 서버 대수, 커넥션 대기 시간 등)로 산정하셨는지, 그리고 그 조정 이후 실제로 커넥션 부족 현상이 개선되었는지 구체적인 수치나 모니터링 결과를 바탕으로 설명해 주시겠어요?',2,NULL,NULL,'FOLLOW_UP',13,'CLAUDE',5),(20,'2026-08-06 14:16:02.295512','답변해주신 내용이 다소 구체적이지 않아 다른 관점에서 여쭤보겠습니다. 트래픽 급증 상황에서 오토스케일링 정책(예: CPU 사용률, 요청 수 등 스케일링 기준)을 어떻게 설정하셨는지, 그리고 실제로 스케일 아웃/인이 발생했을 때 서비스 응답 시간이나 안정성에 어떤 영향이 있었는지 구체적인 수치나 사례를 들어 설명해 주시겠어요?',1,NULL,NULL,'FOLLOW_UP',14,'CLAUDE',5),(21,'2026-08-06 14:16:11.459967','답변해주신 내용도 구체적인 수치나 스케일링 기준 없이 다소 원론적으로 들립니다. 다른 관점에서 여쭤보겠습니다. 로드밸런서(ALB/NLB 등) 설정 시 헬스체크 기준은 어떻게 정의하셨고, 실제 트래픽 급증 상황에서 특정 인스턴스가 비정상 상태로 판단되어 트래픽 분산에서 제외된 경험이 있으신가요? 있다면 그 원인과 대응 과정을 구체적으로 설명해 주세요.',2,NULL,NULL,'FOLLOW_UP',14,'CLAUDE',5),(22,'2026-08-06 14:16:24.055360','답변해주신 내용이 다소 추상적이어서 조금 더 구체적으로 여쭤보겠습니다. Redis 캐싱을 도입하셨다면, 캐시 대상 데이터는 어떤 기준으로 선정하셨고, 캐시 무효화(invalidation) 전략은 어떻게 설계하셨나요? 또한 도입 전후 응답 속도나 서버 부하 지표를 수치로 비교하신 결과가 있다면 함께 말씀해 주세요.',1,NULL,NULL,'FOLLOW_UP',15,'CLAUDE',5),(23,'2026-08-06 14:16:32.362510','답변 내용이 계속 추상적으로 느껴져 다른 각도에서 여쭤보겠습니다. 캐싱 전략 외에 비동기 처리를 도입하신 경험도 있다고 하셨는데, 구체적으로 어떤 작업을 동기에서 비동기로 전환하셨나요? 메시지 큐(예: Kafka, RabbitMQ) 등을 사용하셨다면 어떤 이유로 해당 기술을 선택했는지, 그리고 도입 과정에서 데이터 정합성이나 실패 처리(재시도, 장애 복구)는 어떻게 설계하셨는지 구체적인 사례를 들어 설명해 주시겠어요?',2,NULL,NULL,'FOLLOW_UP',15,'CLAUDE',5),(24,'2026-08-07 14:50:32.236893','대용량 트래픽 상황에서 서버 부하를 분산하기 위해 사용해본 로드밸런싱 기법과 그 선택 이유를 설명해주세요.',1,NULL,NULL,'MAIN',NULL,'CLAUDE',6),(25,'2026-08-07 14:50:32.250587','데이터베이스 병목 현상이 발생했을 때 어떤 방식으로 원인을 진단하고 최적화했는지 구체적인 사례를 들어 설명해주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',6),(26,'2026-08-07 14:50:32.254692','캐시(Cache) 시스템을 도입할 때 캐시 무효화(Cache Invalidation) 전략을 어떻게 설계하셨나요? 발생했던 문제와 해결 방법을 말씀해주세요.',3,NULL,NULL,'MAIN',NULL,'CLAUDE',6),(27,'2026-08-07 14:50:32.261726','MSA(마이크로서비스 아키텍처) 환경에서 서비스 간 통신 지연이나 장애가 발생했을 때 이를 감지하고 대응하기 위한 방법(예: Circuit Breaker, 타임아웃 설정 등)에 대해 설명해주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',6),(28,'2026-08-07 14:50:32.267674','대규모 트래픽이 예상되는 이벤트(예: 프로모션, 세일)를 앞두고 사전에 진행했던 부하 테스트 및 용량 산정 과정에 대해 구체적으로 설명해주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',6),(29,'2026-08-07 14:57:18.761822','재고 차감 로직에서 동시 요청으로 인한 문제를 구체적으로 어떻게 진단하셨나요? 예를 들어 락 경합이나 데드락 현상을 확인하기 위해 어떤 도구나 쿼리(예: slow query log, lock 모니터링 등)를 사용하셨고, 그 결과 어떤 지표(대기 시간, 락 횟수 등)를 통해 병목 지점을 특정하셨는지 설명해주실 수 있을까요?',1,NULL,NULL,'FOLLOW_UP',25,'CLAUDE',6),(30,'2026-08-07 14:58:10.830410','답변이 아직 제공되지 않은 것 같습니다. 다른 각도에서 여쭤보겠습니다. 재고 차감 로직에서 발생한 동시성 문제를 해결하기 위해 구체적으로 어떤 방법(예: 비관적 락, 낙관적 락, 큐를 이용한 순차 처리, 분산 락 등)을 선택하셨고, 그 방법을 선택한 이유와 적용 후 성능이 어떻게 개선되었는지 구체적인 수치로 설명해주실 수 있을까요?',2,NULL,NULL,'FOLLOW_UP',25,'CLAUDE',6),(31,'2026-10-19 13:26:47.343389','본인이 가장 자신 있는 프로그래밍 언어와 프레임워크를 활용하여 백엔드 시스템을 설계한다면, 어떤 아키텍처(예: MVC, 레이어드 아키텍처, 마이크로서비스 등)를 선택하시겠습니까? 그 이유와 함께 설명해주세요.',1,NULL,NULL,'MAIN',NULL,'CLAUDE',7),(32,'2026-10-19 13:26:47.351995','데이터베이스 설계 시 정규화와 비정규화를 어떤 기준으로 선택하시나요? 실제로 트레이드오프를 고려해 설계를 결정했던 경험이 있다면 말씀해주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',7),(33,'2026-10-19 13:26:47.354372','대용량 트래픽이 몰리는 서비스에서 API 응답 속도가 느려지는 문제가 발생했다고 가정할 때, 어떤 방식으로 원인을 분석하고 성능을 개선하시겠습니까?',3,NULL,NULL,'MAIN',NULL,'CLAUDE',7),(34,'2026-10-19 13:26:47.356408','RESTful API를 설계할 때 중요하게 고려하는 원칙은 무엇이며, 인증/인가(예: JWT, OAuth2) 처리는 어떻게 구현하시는지 설명해주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',7),(35,'2026-10-19 13:26:47.358380','서비스 운영 중 장애가 발생했을 때 로그 모니터링 및 장애 대응을 어떻게 진행하는지, 그리고 재발 방지를 위해 어떤 조치를 취하는지 설명해주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',7),(36,'2026-10-19 13:31:54.131893','본인이 가장 자신 있는 백엔드 프로그래밍 언어와 프레임워크는 무엇이며, 그것을 선택한 이유를 설명해 주세요.',1,NULL,NULL,'MAIN',NULL,'CLAUDE',8),(37,'2026-10-19 13:31:54.138402','RESTful API를 설계할 때 고려해야 할 핵심 원칙들은 무엇이며, 실제로 API를 설계해본 경험이 있다면 설명해 주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',8),(38,'2026-10-19 13:31:54.144157','관계형 데이터베이스(RDBMS)와 NoSQL 데이터베이스의 차이점은 무엇이며, 각각 어떤 상황에서 사용하는 것이 적합한지 설명해 주세요.',3,NULL,NULL,'MAIN',NULL,'CLAUDE',8),(39,'2026-10-19 13:31:54.147052','서비스의 트래픽이 급증하는 상황에서 서버의 성능과 안정성을 확보하기 위해 어떤 방법들을 고려할 수 있는지 설명해 주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',8),(40,'2026-10-19 13:31:54.149363','데이터베이스 트랜잭션의 ACID 속성에 대해 설명하고, 동시성 문제(예: 데드락, 레이스 컨디션)를 어떻게 해결할 수 있는지 설명해 주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',8),(41,'2026-10-19 13:39:29.732370','답변 주신 내용은 동시성 문제 해결 경험이었는데, 원래 질문으로 돌아가서 가장 자신 있는 백엔드 언어와 프레임워크가 무엇인지, 그리고 그것을 선택하신 이유를 말씀해 주시겠어요? 추가로 Redisson의 pub/sub 방식으로 전환하면서 Lettuce 스핀락 대비 구체적으로 어떤 성능 개선 수치를 확인하셨는지도 궁금합니다.',1,NULL,NULL,'FOLLOW_UP',36,'CLAUDE',8),(42,'2026-10-19 16:22:24.042320','본인이 가장 자신 있는 프로그래밍 언어와 프레임워크를 선택한 이유와, 해당 기술로 백엔드 시스템을 설계할 때 고려했던 핵심 원칙을 설명해주세요.',1,NULL,NULL,'MAIN',NULL,'CLAUDE',9),(43,'2026-10-19 16:22:24.166751','대용량 트래픽을 처리해야 하는 API 서버를 설계한다고 가정할 때, 데이터베이스 선택부터 캐싱 전략까지 어떤 아키텍처를 구성할지 설명해주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',9),(44,'2026-10-19 16:22:24.169208','RDB와 NoSQL의 차이점을 설명하고, 실제 서비스 상황에서 각각을 어떤 기준으로 선택할지 사례를 들어 설명해주세요.',3,NULL,NULL,'MAIN',NULL,'CLAUDE',9),(45,'2026-10-19 16:22:24.171443','서비스 운영 중 특정 API의 응답 속도가 급격히 느려지는 장애가 발생했다면, 어떤 절차로 원인을 파악하고 해결할 것인지 단계별로 설명해주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',9),(46,'2026-10-19 16:22:24.173262','동시성 문제(Race Condition)가 발생할 수 있는 상황을 예시로 들고, 이를 방지하기 위해 백엔드 레벨에서 적용할 수 있는 기술적 해결책을 설명해주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',9),(47,'2026-10-19 16:24:08.881902','말씀하신 의존성 주입을 스프링이 내부적으로 어떤 방식으로 처리하는지, 그리고 생성자 주입과 필드 주입 중 어떤 것을 선호하며 그 이유는 무엇인지 설명해주시겠어요?',1,NULL,'DEEP','FOLLOW_UP',42,'CLAUDE',9),(48,'2026-10-19 16:51:18.297806','백엔드 개발에서 RESTful API를 설계할 때 고려해야 할 핵심 원칙은 무엇이며, 실제로 API를 설계해본 경험이 있다면 설명해 주세요.',1,NULL,NULL,'MAIN',NULL,'CLAUDE',10),(49,'2026-10-19 16:51:18.303222','데이터베이스 설계 시 정규화와 비정규화를 어떤 기준으로 선택하시나요? 실제 프로젝트에서 성능 이슈를 겪고 해결한 경험이 있다면 말씀해 주세요.',2,NULL,NULL,'MAIN',NULL,'CLAUDE',10),(50,'2026-10-19 16:51:18.305084','대용량 트래픽이 발생하는 서비스에서 서버의 성능을 개선하기 위해 어떤 방법들을 사용할 수 있는지 설명해 주세요(캐싱, 로드밸런싱, 비동기 처리 등).',3,NULL,NULL,'MAIN',NULL,'CLAUDE',10),(51,'2026-10-19 16:51:18.306860','트랜잭션 처리 중 동시성 문제(Race Condition)가 발생할 수 있는 상황을 예로 들고, 이를 어떻게 해결할 수 있는지 설명해 주세요.',4,NULL,NULL,'MAIN',NULL,'CLAUDE',10),(52,'2026-10-19 16:51:18.308330','서비스 운영 중 장애가 발생했을 때, 원인을 파악하고 해결하기 위한 본인만의 디버깅 및 모니터링 프로세스를 설명해 주세요.',5,NULL,NULL,'MAIN',NULL,'CLAUDE',10),(53,'2026-10-19 16:52:27.910787','직접 설계하신 API에서 리소스의 URI 구조와 HTTP 메서드를 구체적으로 어떻게 정의하셨는지, 예를 들어 설명해 주시겠어요?',1,NULL,'DEEP','FOLLOW_UP',48,'CLAUDE',10),(54,'2026-10-19 16:53:40.502793','만약 하나의 리소스에 대해 여러 하위 리소스를 중첩해서 표현해야 하는 상황, 예를 들어 특정 사용자의 주문 목록을 조회해야 한다면 URI 구조를 어떻게 설계하시겠어요?',2,NULL,'WHAT_IF','FOLLOW_UP',48,'CLAUDE',10);
/*!40000 ALTER TABLE `questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `refresh_tokens`
--

DROP TABLE IF EXISTS `refresh_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `refresh_token` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1lih5y2npsf8u5o3vhdb9y0os` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refresh_tokens`
--

LOCK TABLES `refresh_tokens` WRITE;
/*!40000 ALTER TABLE `refresh_tokens` DISABLE KEYS */;
INSERT INTO `refresh_tokens` VALUES (1,'2026-08-04 15:52:13.642273','2026-08-18 15:52:13.641889','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1ODI2MzMzLCJleHAiOjE3ODcwMzU5MzN9.LnUi5NYSb8QCO9gbuAAsYGdFLQqCrpI1LOlRP1HyV8YsH3gliiX44iJaTN9yZKbdGa0Haqg14CFTOPjS6M3SLg',2),(2,'2026-08-04 16:58:57.773466','2026-08-18 16:58:57.765744','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1ODMwMzM3LCJleHAiOjE3ODcwMzk5Mzd9.OYqxpjOodOKXp4uygahvAjcao5I-uReUq3XbtY8r1EIaZg2xZYBkFH6UOqXNjAayFWG6mCzbtONrVC85LU03nQ',2),(3,'2026-08-04 17:00:22.224317','2026-08-18 17:00:22.223282','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1ODMwNDIyLCJleHAiOjE3ODcwNDAwMjJ9.hnVpyhnvQmelQNxfHkiTzFUJEEDiwOYiYoqxigQVRyPaR-vZ88Oi36r8q4odhKC_30JkfTqAybPj2WIa2iZU0A',2),(4,'2026-08-04 17:01:39.185144','2026-08-18 17:01:39.184347','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1ODMwNDk5LCJleHAiOjE3ODcwNDAwOTl9.j3fO4D5X0-beTSE2tBOakeXj5jJivjhgusHKnm0vqr1YGNXmG7tnPo0Jl0URF61FwDR6_coeU4PQ_egbBdk59A',2),(5,'2026-08-04 23:35:11.576530','2026-08-18 23:35:11.569400','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1ODU0MTExLCJleHAiOjE3ODcwNjM3MTF9.sSRqd1IS6W3nJGeeA8oVJnE4lerI4S26PI_PUEp5Ajke7-5Le1k-b15wzh-haVX6-rfneCgtD-1Tc2CITI2VCw',2),(6,'2026-08-06 14:04:56.594387','2026-08-20 14:04:56.585229','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1OTkyNjk2LCJleHAiOjE3ODcyMDIyOTZ9.gA35jjDCkx-Q5GT1zVsJZhnWmuXf1GXioaBjyMihoR9Ar3XX5nK0r9jS_vu1pMGEsaHAH_lCtwSbctbp9aHa8Q',2),(7,'2026-08-06 14:09:05.329294','2026-08-20 14:09:05.322939','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg1OTkyOTQ1LCJleHAiOjE3ODcyMDI1NDV9.d00pQ1o7A4JUBUaHoJ61ejt78_DSUtSxTH7u1CPRaj-YBkAtbOsb9_AZX7PWogVWV7GqGLYYiP__f-c3n6Wx1A',2),(8,'2026-08-07 14:48:53.210856','2026-08-21 14:48:53.203486','eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiaWF0IjoxNzg2MDgxNzMzLCJleHAiOjE3ODcyOTEzMzN9.bVLXixTnKejCf79QgUpdzBcjxa58peSgUIZnht96YyVkz47Pmq1fjCfoKOmMUbL_SEZ4cNJ_Nts1TAqTaR7TUA',2);
/*!40000 ALTER TABLE `refresh_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_axes`
--

DROP TABLE IF EXISTS `score_axes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_axes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `axis` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score` int NOT NULL,
  `feedback_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8xxk5xedn3mbukr7htsg9ahi3` (`feedback_id`),
  CONSTRAINT `FK8xxk5xedn3mbukr7htsg9ahi3` FOREIGN KEY (`feedback_id`) REFERENCES `feedbacks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_axes`
--

LOCK TABLES `score_axes` WRITE;
/*!40000 ALTER TABLE `score_axes` DISABLE KEYS */;
/*!40000 ALTER TABLE `score_axes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `seed_questions`
--

DROP TABLE IF EXISTS `seed_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seed_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `company_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `stage` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `seed_questions`
--

LOCK TABLES `seed_questions` WRITE;
/*!40000 ALTER TABLE `seed_questions` DISABLE KEYS */;
INSERT INTO `seed_questions` VALUES (1,'2026-07-30 17:16:31.000000','SERVICE','최근 진행한 프로젝트에서 기술적으로 가장 어려웠던 문제와 해결 과정을 설명해주세요.','TECHNICAL'),(2,'2026-07-30 17:16:31.000000','SERVICE','RESTful API를 설계할 때 중요하게 고려하는 원칙은 무엇인가요?','TECHNICAL'),(3,'2026-07-30 17:16:31.000000','SERVICE','데이터베이스 인덱스가 무엇이고, 언제 사용하면 좋은지 설명해주세요.','TECHNICAL'),(4,'2026-07-30 17:16:31.000000','SERVICE','대용량 트래픽을 처리하기 위한 방법을 아는 대로 설명해주세요.','TECHNICAL'),(5,'2026-07-30 17:16:31.000000','SERVICE','캐시(Cache)를 사용하는 이유와 발생할 수 있는 문제점은 무엇인가요?','TECHNICAL'),(6,'2026-07-30 17:16:31.000000','BIG_TECH_SW','객체지향 프로그래밍의 핵심 개념 4가지를 설명해주세요.','TECHNICAL'),(7,'2026-07-30 17:16:31.000000','BIG_TECH_SW','프로세스와 스레드의 차이를 설명해주세요.','TECHNICAL'),(8,'2026-07-30 17:16:31.000000','BIG_TECH_SW','TCP와 UDP의 차이점과 각각의 사용 사례를 설명해주세요.','TECHNICAL'),(9,'2026-07-30 17:16:31.000000','BIG_TECH_SW','자료구조 중 해시테이블의 동작 원리와 시간복잡도를 설명해주세요.','TECHNICAL'),(10,'2026-07-30 17:16:31.000000','BIG_TECH_SW','동시성 문제가 무엇이고 어떻게 해결할 수 있는지 설명해주세요.','TECHNICAL');
/*!40000 ALTER TABLE `seed_questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `session_feedbacks`
--

DROP TABLE IF EXISTS `session_feedbacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `session_feedbacks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `coaching_action` text COLLATE utf8mb4_unicode_ci,
  `filler_word_count` int DEFAULT NULL,
  `filler_word_note` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `key_coaching` text COLLATE utf8mb4_unicode_ci,
  `speech_speed` int DEFAULT NULL,
  `speech_speed_note` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_score` int DEFAULT NULL,
  `session_id` bigint NOT NULL,
  `scoring_status` enum('COMPLETED','FAILED','SCORING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8bfynhdhy1ph2nn421wnpoogr` (`session_id`),
  CONSTRAINT `FK21tl0bofa71x4lg66qjq6bep6` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `session_feedbacks`
--

LOCK TABLES `session_feedbacks` WRITE;
/*!40000 ALTER TABLE `session_feedbacks` DISABLE KEYS */;
/*!40000 ALTER TABLE `session_feedbacks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sessions`
--

DROP TABLE IF EXISTS `sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `company_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKruie73rneumyyd1bgo6qw8vjt` (`user_id`),
  CONSTRAINT `FKruie73rneumyyd1bgo6qw8vjt` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sessions`
--

LOCK TABLES `sessions` WRITE;
/*!40000 ALTER TABLE `sessions` DISABLE KEYS */;
INSERT INTO `sessions` VALUES (1,'2026-07-31 18:34:54.408974',NULL,'2026-07-31 18:34:54.408974','카카오','SERVICE','백엔드','TECHNICAL','GENERATING',1),(2,'2026-07-31 18:44:22.130539',NULL,'2026-07-31 18:44:22.130539','카카오','SERVICE','백엔드','TECHNICAL','GENERATING',1),(3,'2026-07-31 18:55:24.657921',NULL,'2026-07-31 18:55:25.261504','카카오','SERVICE','백엔드','TECHNICAL','READY',1),(4,'2026-07-31 21:29:41.610048',NULL,'2026-08-02 16:54:13.652640','카카오','SERVICE','백엔드','TECHNICAL','IN_PROGRESS',1),(5,'2026-08-04 17:04:18.761516',NULL,'2026-08-06 14:18:09.043672','카카오','SERVICE','백엔드','TECHNICAL','ANALYZING',2),(6,'2026-08-07 14:50:25.786818',NULL,'2026-08-07 14:58:40.782367','카카오','SERVICE','백엔드','TECHNICAL','CANCELED',2),(7,'2026-10-19 13:26:40.941683',NULL,'2026-10-19 13:26:47.360401','카카오','SERVICE','백엔드','TECHNICAL','READY',6),(8,'2026-10-19 13:31:49.359323',NULL,'2026-10-19 13:34:22.348046','카카오','SERVICE','백엔드','TECHNICAL','IN_PROGRESS',7),(9,'2026-10-19 16:22:18.369884',NULL,'2026-10-19 16:22:41.507385','카카오','SERVICE','백엔드','TECHNICAL','IN_PROGRESS',8),(10,'2026-10-19 16:51:13.068522',NULL,'2026-10-19 16:51:23.724361','카카오','SERVICE','백엔드','TECHNICAL','IN_PROGRESS',9);
/*!40000 ALTER TABLE `sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `kakao_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `terms_agreed_at` datetime(6) DEFAULT NULL,
  `user_type` enum('GUEST','MEMBER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKk4ycaj27putgcujmehwbsrmmc` (`kakao_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'2026-07-31 18:31:33.000000',NULL,'2026-07-31 18:31:33.000000','test123','테스트유저','2026-07-31 18:31:33.000000','MEMBER'),(2,'2026-08-04 15:52:13.568761',NULL,'2026-08-04 15:52:13.568761','5022719043','카카오사용자','2026-08-04 15:52:14.000000','MEMBER'),(3,'2026-10-19 13:06:44.484235',NULL,'2026-10-19 13:06:44.484235',NULL,'게스트',NULL,'GUEST'),(4,'2026-10-19 13:09:41.041448',NULL,'2026-10-19 13:09:41.041448',NULL,'게스트',NULL,'GUEST'),(5,'2026-10-19 13:12:19.439017',NULL,'2026-10-19 13:12:19.439017',NULL,'게스트',NULL,'GUEST'),(6,'2026-10-19 13:25:32.924877',NULL,'2026-10-19 13:25:32.924877',NULL,'게스트',NULL,'GUEST'),(7,'2026-10-19 13:31:43.393379',NULL,'2026-10-19 13:31:43.393379',NULL,'게스트',NULL,'GUEST'),(8,'2026-10-19 16:22:03.933443',NULL,'2026-10-19 16:22:03.933443',NULL,'게스트',NULL,'GUEST'),(9,'2026-10-19 16:51:00.654061',NULL,'2026-10-19 16:51:00.654061',NULL,'게스트',NULL,'GUEST');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-04 14:29:42
