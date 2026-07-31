#!/bin/bash
# 一键编译、打包、运行 PeerMesh 测试
# 适用系统：Linux / macOS（Windows 可用 Git Bash）

set -e

# 工作区（改成你的实际路径）
PROJECT_ROOT="/g/work/utils-support-parent-starter/utils-support-extra-parent/utils-support-example-starter"

cd "$PROJECT_ROOT"

echo "=== 1. 编译项目 ==="
mvn clean compile -DskipTests

echo ""
echo "=== 2. 测试运行 SEED 节点（后台）==="
# 后台运行 SEED 节点
nohup mvn exec:java -Dexec.mainClass="com.chua.example.network.discovery.peermesh.PeerMeshSeedExample" \
  -Dexec.args="--port 9876 --bind-ip 127.0.0.1" > seed.log 2>&1 &
echo $! > seed.pid
echo "SEED PID: $(cat seed.pid)"
sleep 5
echo "SEED 日志（前20行）："
head -20 seed.log

echo ""
echo "=== 3. 测试运行 C-SCAN 节点（前台，10秒后退出）==="
timeout 10 mvn exec:java -Dexec.mainClass="com.chua.example.network.discovery.peermesh.PeerMeshCScanExample" \
  -Dexec.args="--port 9877 --bind-ip 127.0.0.1 --seed 127.0.0.1:9876" 2>&1 | head -50

echo ""
echo "=== 4. 停止 SEED ==="
if [ -f seed.pid ]; then
  kill $(cat seed.pid) 2>/dev/null || true
  rm -f seed.pid
fi

echo "完成。查看 seed.log 和 C-SCAN 输出了什么。"
