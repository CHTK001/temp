#!/bin/bash
# 打包 PeerMesh 示例为可执行 jar（包含所有依赖）

set -e

PROJECT_ROOT="/path/to/utils-support-extra-parent/utils-support-example-starter"

echo "=== 1. 编译 ==="
mvn -f "$PROJECT_ROOT/pom.xml" clean compile -DskipTests

echo "=== 2. 打包 fat-jar ==="
mvn -f "$PROJECT_ROOT/assembly/peermesh-deploy/pom.xml" clean package -DskipTests

echo "=== 3. 输出 ==="
ls -lh "$PROJECT_ROOT/target"/*.jar

echo ""
echo "待会这样运行："
echo "  SEED节点:"
echo "    java -jar target/peermesh-deploy-4.0.0.42.jar --port 9876 --bind-ip 172.16.0.40"
echo ""
echo "  C扫描节点:"
echo "    java -jar target/peermesh-deploy-4.0.0.42.jar --port 9877 --bind-ip 172.16.0.40 --seed 172.16.0.40:9876 --subnet 172.16.0.0/16"
