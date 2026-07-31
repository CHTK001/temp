FROM eclipse-temurin:21-jre

LABEL maintainer="CH"
LABEL description="PeerMesh Discovery - P2P Service Discovery"

# 创建工作目录
WORKDIR /app

# 复制 Maven 本地仓库缓存（如果有），加快构建速度
# COPY settings.xml /root/.m2/settings.xml

# 复制已编译的 jar 和依赖
COPY target/classes /app/classes
COPY target/dependency /app/dependency

# 暴露端口
EXPOSE 9876 9877

# 启动命令（默认启动 SEED 节点）
ENTRYPOINT ["java", "-cp", "/app/classes:/app/dependency/*"]
CMD ["com.chua.example.network.discovery.peermesh.PeerMeshSeedExample", "--port", "9876", "--bind-ip", "0.0.0.0"]
