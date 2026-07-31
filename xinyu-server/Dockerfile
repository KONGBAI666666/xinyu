# ============================================================
# 心屿后端镜像 (M1-7): 多阶段构建, 产物只含 JRE + jar
#   stage 1  maven + JDK21 编译打包 (依赖层与源码层分离, 改代码不重拉依赖)
#   stage 2  JRE21 运行 (最终镜像不含源码与构建工具链)
# 配置注入: 数据源/JWT/API Key 全部走环境变量 (见 docker-compose.yml),
#           镜像内不存在任何敏感配置 (application-dev/prod.yml 已被 .dockerignore 排除)
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先只拷 pom 拉依赖, 利用 Docker 层缓存: 源码变更不触发依赖重新下载
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
# 测试依赖本机 MySQL, 构建镜像时跳过 (回归测试在 CI/本机跑)
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/*.jar app.jar
# 仅在 compose 内网暴露, 不直接发布到宿主机 (统一从 nginx 进入)
EXPOSE 9000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
