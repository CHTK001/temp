package com.chua.example.docker;

import com.chua.common.support.lang.json.Json;
import com.chua.docker.support.client.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;

import java.util.List;

/**
 * DockerClient 测试示例
 *
 * <p>连接远程 Docker 守护进程，测试容器、镜像、系统信息等操作。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DockerClientExample {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "172.16.0.40";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2375;

        System.out.println("========== DockerClient 测试开始 ==========");
        System.out.println("目标: " + host + ":" + port);

        try (DockerClient docker = DockerClient.builder()
                .tcp(host, port)
                .connectTimeout(5000)
                .readTimeout(30000)
                .build()) {

            // 1. 测试连通性
            System.out.println("\n--- 1. Ping ---");
            try {
                docker.ping();
                System.out.println("PASS: Docker 守护进程可达");
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                return;
            }

            // 2. 系统信息
            System.out.println("\n--- 2. 系统信息 ---");
            try {
                Info info = docker.info();
                System.out.println("PASS: " + Json.toJson(info));
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            // 3. 版本信息
            System.out.println("\n--- 3. 版本信息 ---");
            try {
                var version = docker.version();
                System.out.println("PASS: 版本=" + version.getVersion()
                        + ", API=" + version.getApiVersion()
                        + ", OS=" + version.getOperatingSystem());
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            // 4. 容器列表
            System.out.println("\n--- 4. 容器列表 ---");
            try {
                List<Container> containers = docker.container().list().all(true).execSync();
                System.out.println("PASS: 共 " + containers.size() + " 个容器");
                for (Container c : containers) {
                    System.out.println("  - [" + c.getState() + "] " + c.getId().substring(0, 12)
                            + " " + String.join(",", c.getNames()));
                }
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            // 5. 镜像列表
            System.out.println("\n--- 5. 镜像列表 ---");
            try {
                List<Image> images = docker.image().list().exec();
                System.out.println("PASS: 共 " + images.size() + " 个镜像");
                images.stream().limit(10).forEach(i ->
                        System.out.println("  - " + String.join(",", i.getRepoTags())));
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            // 6. 容器操作测试（创建+启动+停止+删除一个临时容器）
            System.out.println("\n--- 6. 容器生命周期测试 ---");
            try {
                String id = docker.container().create("docker-test-" + System.currentTimeMillis())
                        .image("hello-world:latest")
                        .exec();
                System.out.println("PASS: 创建容器 " + id.substring(0, 12));

                docker.container().start(id).execSync();
                System.out.println("PASS: 启动容器");

                // 等待容器退出
                docker.container().wait(id, 30);
                System.out.println("PASS: 容器执行完成");

                // 获取日志
                String logs = docker.container().logs(id).tail(20).execSync();
                System.out.println("PASS: 日志获取成功 (" + logs.length() + " 字符)");

                docker.container().remove(id).force(true).execSync();
                System.out.println("PASS: 删除容器");
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

            // 7. Exec 操作测试
            System.out.println("\n--- 7. Exec 容器命令测试 ---");
            try {
                // 找一个运行中的容器来执行命令
                List<Container> running = docker.container().list().execSync();
                if (!running.isEmpty()) {
                    String targetId = running.get(0).getId();
                    String output = docker.container().exec(targetId)
                            .command("uname", "-a")
                            .execSync();
                    System.out.println("PASS: exec 输出: " + output.trim());
                } else {
                    System.out.println("SKIP: 没有运行中的容器");
                }
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("初始化失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n========== DockerClient 测试结束 ==========");
    }
}