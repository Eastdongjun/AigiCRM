# Spring Boot 4 升级实施记录(AIGICRM,2026-08-15)

> 分支:`feat/spring-boot4-upgrade`(基于 main 创建,可随时回滚 `git switch main && git branch -D feat/spring-boot4-upgrade`)
> 触发:应用启动告警「OSS support for Spring Boot 3.5.x ended on 2026-06-30」
> 目标:Spring Boot **4.1.0**(免费支持至 ~2027-06)

## 一、依赖矩阵变更(全部经 Maven Central 权威元数据核实)

| 依赖 | 升级前 | 升级后 |
|---|---|---|
| spring-boot-starter-parent | 3.5.16 | **4.1.0** |
| mybatis-plus | spring-boot3-starter 3.5.12 | **spring-boot4-starter 3.5.17** |
| mybatis-plus-jsqlparser | 3.5.12 | **3.5.17** |
| springdoc | 2.8.17(覆盖 knife4j) | **3.1.0**(Spring 7 线) |
| knife4j | 4.5.0(停更) | **移除**(UI 换 swagger-ui) |
| spring-boot-starter-aop | 有 | **移除**(Boot 4 取消该 starter → 直依赖 aspectjweaver 1.9.25.1,BOM 管理) |
| jjwt | 0.12.5 | **0.13.0** |
| flyway-core(运行期) | 11.7.2(BOM) | **12.4.0**(Boot 4.1 BOM,实测依赖树) |
| flyway-maven-plugin | 11.7.2 | **12.4.0**(与 core 对齐) |
| mockito-core | 5.17.0(BOM) | 5.17.0(不变) |

## 二、代码迁移点(3 处,全部小改)

1. `PermissionCacheServiceTest.java:51` — Spring Data Redis 2026 新增 `set(K,V,SetOption)`
   重载导致 `any()` 歧义 → 改 `any(Duration.class)` + 补 import。
2. `PermissionSecurityFixTest` / `FolderPermissionServiceTest` — MyBatis-Plus 3.5.17 起
   纯 mock 环境必须显式 `TableInfoHelper.initTableInfo` 注册实体 lambda 缓存
   (与 FileServicePermissionTest 既有模式一致),否则服务内 LambdaQueryWrapper 抛
   `can not find lambda cache`。两个类各补 @BeforeEach 初始化。
3. `SecurityConfig.java:46` — 移除 knife4j 的 `/doc.html/**` 放行(依赖已删);
   `application.yml` 注释同步(springdoc 属性本身 3.x 兼容,未改)。

## 三、验证结果

- `mvn clean test`(JDK 17):**Tests run: 412, Failures: 0, Errors: 0 → BUILD SUCCESS** ✅
- 前端 `npm run build`:✅ 687ms
- verify.sh / 28 道门禁:见 `engineering/evidence/boot4-verify-20260815.log`

## 四、回滚方式

```bash
git switch main          # 回到升级前(3.5.16 分支)
git branch -D feat/spring-boot4-upgrade   # 确认丢弃升级分支后执行
```
升级分支未合并、未提交前,main 完全不受影响。

## 五、部署前置与残余风险(⚠️ 需用户决策)

1. **生产 V16 迁移部署**:`deploy-db.sh` 直连生产(SSH+root),必须用户确认后执行;
2. **生产 Flyway 镜像版本偏斜**:deploy-db.sh 锁 `flyway/flyway:9.22.3`,而运行期 core 已是
   12.4.0 —— 生产迁移执行器与应用运行期跨大版本,建议治理任务把镜像升到 12.4.0 并演练一次;
3. **接口文档入口变化**:knife4j 移除后文档 UI 从 `/doc.html` 变为 swagger-ui
   (`/swagger-ui/index.html`,yml 配置 `/swagger-ui.html` 重定向)——需起服冒烟确认,
   前端如有 /doc.html 跳转链接需同步改;
4. 升级分支的代码改动**未提交**(提交说明建议:
   `feat(backend): 升级 Spring Boot 4.1 + MyBatis-Plus 3.5.17 + springdoc 3.1(移除 knife4j)`);
5. 生产部署新版本镜像需走既有 deploy-backend 流程(用户操作)。
