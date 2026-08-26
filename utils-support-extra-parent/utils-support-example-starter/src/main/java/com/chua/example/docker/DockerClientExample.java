package com.chua.example.docker;

import com.chua.common.support.lang.json.Json;
import com.chua.docker.support.client.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * DockerClient 测试示例
 *
 * <p>连接远程 Docker 守护进程，测试容器、镜像、系统信息等操作。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DockerClientExample {
    private DockerClientExample() { }


    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "172.16.0.40";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2375;

        log.info("========== DockerClient 测试开始 ==========");
        log.info("目标: " + host + ":" + port);

        try (DockerClient docker = DockerClient.builder()
                .tcp(host, port)
                .connectTimeout(5000)
                .readTimeout(30000)
                .build()) {

            // 1. 测试连通性
            log.info("\n--- 1. Ping ---");
            try {
                docker.ping();
                log.info("PASS: Docker 守护进程可达");
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
                return;
            }

            // 2. 系统信息
            log.info("\n--- 2. 系统信息 ---");
            try {
                Info info = docker.info();
                log.info("PASS: " + Json.toJson(info));
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
            }

            // 3. 版本信息
            log.info("\n--- 3. 版本信息 ---");
            try {
                var version = docker.version();
                System.out.println("PASS: 版本=" + version.getVersion()
                        + ", API=" + version.getApiVersion()
                        + ", OS=" + version.getOperatingSystem());
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
            }

            // 4. 容器列表
            log.info("\n--- 4. 容器列表 ---");
            try {
                List<Container> containers = docker.container().list().all(true).execSync();
                log.info("PASS: 共 " + containers.size() + " 个容器");
                for (Container c : containers) {
                    System.out.println("  - [" + c.getState() + "] " + c.getId().substring(0, 12)
                            + " " + String.join(",", c.getNames()));
                }
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
            }

            // 5. 镜像列表
            log.info("\n--- 5. 镜像列表 ---");
            try {
                List<Image> images = docker.image().list().exec();
                log.info("PASS: 共 " + images.size() + " 个镜像");
                images.stream().limit(10).forEach(i ->
                        log.info("  - " + String.join(",", i.getRepoTags())));
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
            }

            // 6. 容器操作测试（创建+启动+停止+删除一个临时容器）
            log.info("\n--- 6. 容器生命周期测试 ---");
            try {
                String id = docker.container().create("docker-test-" + System.currentTimeMillis())
                        .image("hello-world:latest")
                        .exec();
                log.info("PASS: 创建容器 " + id.substring(0, 12));

                docker.container().start(id).execSync();
                log.info("PASS: 启动容器");

                // 等待容器退出
                docker.container().wait(id, 30);
                log.info("PASS: 容器执行完成");

                // 获取日志
                String logs = docker.container().logs(id).tail(20).execSync();
                log.info("PASS: 日志获取成功 (" + logs.length() + " 字符)");

                docker.container().remove(id).force(true).execSync();
                log.info("PASS: 删除容器");
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
            }

            // 7. Exec 操作测试
            log.info("\n--- 7. Exec 容器命令测试 ---");
            try {
                // 找一个运行中的容器来执行命令
                List<Container> running = docker.container().list().execSync();
                if (!running.isEmpty()) {
                    String targetId = running.get(0).getId();
                    String output = docker.container().exec(targetId)
                            .command("uname", "-a")
                            .execSync();
                    log.info("PASS: exec 输出: " + output.trim());
                } else {
                    log.info("SKIP: 没有运行中的容器");
                }
            } catch (Exception e) {
                log.info("FAIL: " + e.getMessage());
            }

        } catch (Exception e) {
            log.info("初始化失败: " + e.getMessage());
            e.printStackTrace();
        }

        log.info("\n========== DockerClient 测试结束 ==========");
    }
}