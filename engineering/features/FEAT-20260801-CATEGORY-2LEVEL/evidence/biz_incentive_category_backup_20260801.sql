-- MySQL dump 10.13  Distrib 8.0.28, for Linux (x86_64)
--
-- Host: localhost    Database: ycmn
-- ------------------------------------------------------
-- Server version	8.0.28

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
-- Table structure for table `biz_incentive_category`
--

DROP TABLE IF EXISTS `biz_incentive_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_incentive_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID(0为四项大类)',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类名称',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分类编码',
  `score` int DEFAULT '0' COMMENT '分值',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='激励分类配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_incentive_category`
--

LOCK TABLES `biz_incentive_category` WRITE;
/*!40000 ALTER TABLE `biz_incentive_category` DISABLE KEYS */;
INSERT INTO `biz_incentive_category` VALUES (1,0,'外出参观','visit',0,1,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(2,0,'交流拜访','exchange',0,2,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(3,0,'两谈两做方案','plan',0,3,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(4,0,'产品试点','trial',0,4,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(10,1,'终端考察','visit_mine',10,2,1,'2026-06-10 09:44:24','2026-07-27 14:46:44'),(11,1,'南京工厂参观','visit_factory',10,1,1,'2026-06-10 09:44:24','2026-07-27 14:46:38'),(20,2,'技术交流会','exchange_tech',5,1,1,'2026-06-10 09:44:24','2026-06-25 09:59:04'),(21,2,'高层拜访','exchange_biz',5,2,1,'2026-06-10 09:44:24','2026-06-25 09:59:08'),(30,3,'项目方案','plan_tech',2,1,1,'2026-06-10 09:44:24','2026-06-25 08:58:31'),(31,3,'价值服务手册','plan_biz',2,2,1,'2026-06-10 09:44:24','2026-06-25 08:58:54'),(32,3,'使用报告','plan_coop',2,3,1,'2026-06-10 09:44:24','2026-06-25 08:59:06'),(40,4,'涂层类','trial_coating',1,1,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(41,4,'楼梯板类','trial_stair',1,2,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(42,4,'机封类','trial_seal',1,3,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(43,4,'密封类','trial_gasket',1,4,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(44,4,'润滑类','trial_lubricant',1,5,1,'2026-06-10 09:44:24','2026-06-10 09:44:24'),(46,45,'213','22',1,0,1,'2026-06-12 17:34:14','2026-06-12 17:34:14'),(47,45,'213','213',2,1,1,'2026-06-12 17:34:24','2026-06-12 17:34:24'),(48,45,'111','111',2,0,1,'2026-06-12 17:34:31','2026-06-12 17:34:31');
/*!40000 ALTER TABLE `biz_incentive_category` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-01 19:11:04
