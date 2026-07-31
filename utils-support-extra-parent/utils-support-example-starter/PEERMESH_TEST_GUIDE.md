# PeerMesh 真实网络测试指南

## 📦 前置条件

1. 确保 `utils-support-common-starter` 已编译
   ```bash
   cd utils-support-parent-starter/utils-support-core-parent/utils-support-common-starter
   mvn clean install -DskipTests
   ```

2. 确保 `utils-support-example-starter` 已编译
   ```bash
   cd utils-support-extra-parent/utils-support-example-starter
   mvn clean compile -DskipTests
   ```

## 🧪 测试场景

### 场景1: 本地双节点互发现（验证基本功能）

**终端1 - 启动 SEED 节点**（绑定到 127.0.0.1:9876）
```bash
cd utils-support-extra-parent/utils-support-example-starter
mvn exec:java -Dexec.mainClass="com.chua.example.network.discovery.peermesh.PeerMeshSeedExample" ^
  -Dexec.args="--port 9876 --bind-ip 127.0.0.1"
```

**终端2 - 启动 C-SEED 客户端**（绑定到 127.0.0.1:9877，指向种子）
```bash
cd utils-support-extra-parent/utils-support-example-starter
mvn exec:java -Dexec.mainClass="com.chua.example.network.discovery.peermesh.PeerMeshCScanExample" ^
  -Dexec.args="--port 9877 --bind-ip 127.0.0.1 --seed 127.0.0.1:9876"
```

**预期输出**：
- SEED 节点：`[SEED] PeerMesh 种子节点启动完成`
- C 节点：`节点表大小` 应该 >= 2（包含自己和 seed）
- C 节点最终输出：`✅ [PASS] 发现 2 个节点`

---

### 场景2: 真实 172 网段互发现

**机器A（172.16.238.100） - 启动 SEED 节点**
```bash
mvn exec:java -Dexec.mainClass="com.chua.example.network.discovery.peermesh.PeerMeshSeedExample" ^
  -Dexec.args="--port 30010 --bind-ip 172.16.238.100 --peers-file C:\temp\seed_peers.json"
```

**机器B（172.16.238.101） - 启动 C 节点扫描 172.16.0.0/12**
```bash
mvn exec:java -Dexec.mainClass="com.chua.example.network.discovery.peermesh.PeerMeshCScanExample" ^
  -Dexec.args="--port 30011 --bind-ip 172.16.238.101 --seed 172.16.238.100:30010 ^
  --subnet 172.16.0.0/12 --peers-file C:\temp\client_peers.json"
```

**验证互发现**：
- B 节点输出：`发现的节点总数: 2`
- A 节点输出：`节点表大小: 2`
- 两节点之间应能通过 `discovery.getService("/")` 看到对方

---

### 场景3: 节点掉线自动剔除

**步骤**：
1. 按场景1或2启动两个节点
2. 等待约 10 秒确保节点表稳定
3. 在客户端执行 `Ctrl+C` 模拟掉线
4. 在 SEED 节点观察节点表变化

**预期**：
- SEED 节点日志：`节点剔除: <client-serverId>`
- SEED 节点 `nodeSize()` 应减少 1
- 剔除时间 ≈ `evictTimeout`（默认 15 秒）

---

### 场景4: 节点重启重新加入

**步骤**：
1. 按照场景3让客户端掉线
2. 等待 SEED 完成剔除
3. 重新启动客户端（使用相同 ip:port 和 seed 地址）
4. 观察 SEED 节点的节点表

**预期**：
- 客户端重启后，自动重新向 SEED 发送 NEW_PEER 消息
- SEED 节点日志：看到新节点/重连节点
- 节点表恢复到 2
- 客户端能发现 SEED

---

## 🔍 验证检查点

| 检查项 | 验证方法 |
|--------|----------|
| 进程监听 | `netstat -an \| findstr :<port>` |
| 节点表 | 日志输出 `节点表大小: N` |
| 服务发现 | `discovery.getServiceAll("/").size()` |
| 心跳通信 | 日志 `心跳发送至` |
| 剔除事件 | 日志 `节点剔除:` |
| 持久化 | 指定 `--peers-file` 后重启自动加载 |

---

## 🐛 常见问题排查

1. **端口占用**
   ```powershell
   netstat -ano | findstr :9876
   taskkill /PID <pid> /F
   ```

2. **网段扫描慢**
   - 172.16.0.0/12 包含约 100 万个 IP，建议使用更小的网段如 `172.16.238.0/24`

3. **节点不互发现**
   - 检查防火墙：`netsh advfirewall firewall add rule name="PeerMesh" protocol=TCP localport=9876-9877 action=allow`
   - 确保 bind-ip 为实际网卡 IP（非 127.0.0.1）

4. **日志不全**
   - 添加 `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` JVM 参数

---

## 📊 测试检查清单

- [ ] 本地双节点能互相发现
- [ ] 172 网段节点能互相发现
- [ ] 10 秒内未收到心跳的节点被自动剔除
- [ ] 被剔除的节点重启后能重新加入
- [ ] 使用 `--peers-file` 持久化后重启自动恢复节点表

---
生成的测试样例路径：
- `utils-support-extra-parent/utils-support-example-starter/src/main/java/com/chua/example/network/discovery/peermesh/PeerMeshSeedExample.java`
- `utils-support-extra-parent/utils-support-example-starter/src/main/java/com/chua/example/network/discovery/peermesh/PeerMeshCScanExample.java`
