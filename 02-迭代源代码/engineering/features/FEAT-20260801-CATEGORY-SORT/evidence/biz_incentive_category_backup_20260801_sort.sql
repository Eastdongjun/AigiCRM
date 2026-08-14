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
-- Dumping data for table `biz_incentive_category`
--

LOCK TABLES `biz_incentive_category` WRITE;
/*!40000 ALTER TABLE `biz_incentive_category` DISABLE KEYS */;
INSERT INTO `biz_incentive_category` VALUES (1,0,'外出参观','visit',0,1,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (2,0,'交流拜访','exchange',0,2,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (3,0,'两谈两做方案','plan',0,3,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (4,0,'产品试点','trial',0,4,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (10,1,'终端考察','visit_mine',10,2,1,'2026-06-10 09:44:24','2026-07-27 14:46:44');
INSERT INTO `biz_incentive_category` VALUES (11,1,'南京工厂参观','visit_factory',10,1,1,'2026-06-10 09:44:24','2026-07-27 14:46:38');
INSERT INTO `biz_incentive_category` VALUES (20,2,'技术交流会','exchange_tech',5,1,1,'2026-06-10 09:44:24','2026-06-25 09:59:04');
INSERT INTO `biz_incentive_category` VALUES (21,2,'高层拜访','exchange_biz',5,2,1,'2026-06-10 09:44:24','2026-06-25 09:59:08');
INSERT INTO `biz_incentive_category` VALUES (30,3,'项目方案','plan_tech',2,1,1,'2026-06-10 09:44:24','2026-06-25 08:58:31');
INSERT INTO `biz_incentive_category` VALUES (31,3,'价值服务手册','plan_biz',2,2,1,'2026-06-10 09:44:24','2026-06-25 08:58:54');
INSERT INTO `biz_incentive_category` VALUES (32,3,'使用报告','plan_coop',2,3,1,'2026-06-10 09:44:24','2026-06-25 08:59:06');
INSERT INTO `biz_incentive_category` VALUES (40,4,'涂层类','trial_coating',1,1,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (41,4,'楼梯板类','trial_stair',1,2,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (42,4,'机封类','trial_seal',1,3,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (43,4,'密封类','trial_gasket',1,4,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
INSERT INTO `biz_incentive_category` VALUES (44,4,'润滑类','trial_lubricant',1,5,1,'2026-06-10 09:44:24','2026-06-10 09:44:24');
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

-- Dump completed on 2026-08-01 19:50:45
