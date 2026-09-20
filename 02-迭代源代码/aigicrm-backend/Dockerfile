# 【PR-23.D-04 工具链冻结】与 mvnw wrapper 同版(3.9.9), 避免浮动 tag 漂移
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
# 【2026-09-13 修复上传失败】预建上传目录并归 appuser 所有：
# 命名卷首次挂载时会继承镜像内该目录的属主，否则卷根目录是 root:root 755，
# 以 10001 运行的进程无法创建 scope 子目录 ⇒ 所有上传报 "文件上传失败: /app/uploads/public"。
RUN useradd --system --uid 10001 --create-home appuser \
 && mkdir -p /app/uploads/public /app/uploads/private \
 && chown -R 10001:10001 /app/uploads
COPY --from=build /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
