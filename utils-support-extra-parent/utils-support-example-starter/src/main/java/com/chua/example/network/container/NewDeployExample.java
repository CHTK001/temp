package com.chua.example.network.container;

import com.chua.common.support.network.container.AbstractWebContainer;
import com.chua.common.support.network.container.DeployUnitType;
import com.chua.common.support.network.container.WebContainer;
import com.chua.common.support.network.container.WebContainerSetting;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebContainer 新型部署示例 — 演示 FAT_JAR、SPRING_BOOT、MAIN 三种部署类型。
 *
 * <p>使用 TestWebContainer（AbstractWebContainer 的最小实现）验证：</p>
 * <ul>
 *   <li>WAR / EAR — 传统类型仍然可用</li>
 *   <li>FAT_JAR — 可执行 FAT-JAR 部署</li>
 *   <li>SPRING_BOOT — Spring Boot JAR 部署</li>
 *   <li>MAIN — Main 类作为 Web 入口启动</li>
 *   <li>异常场景 — 空路径等错误处理</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class NewDeployExample {

    static final AtomicBoolean mainStarted = new AtomicBoolean(false);
    static final CountDownLatch mainLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        NewDeployExample example = new NewDeployExample();
        boolean passed = example.runAllTests();
        System.exit(passed ? 0 : 1);
    }

    boolean runAllTests() {
        int passed = 0;
        int total = 6;

        println("TC-01", "WAR 部署（传统类型兼容）");
        if (testDeployType(DeployUnitType.WAR, "/myapp.war")) {
            passed++;
        }

        println("TC-02", "EAR 部署（传统类型兼容）");
        if (testDeployType(DeployUnitType.EAR, "/myapp.ear")) {
            passed++;
        }

        println("TC-03", "FAT_JAR 部署（新型）");
        if (testDeployType(DeployUnitType.FAT_JAR, "/myapp-fat.jar")) {
            passed++;
        }

        println("TC-04", "SPRING_BOOT 部署（新型）");
        if (testDeployType(DeployUnitType.SPRING_BOOT, "/myapp-boot.jar")) {
            passed++;
        }

        println("TC-05", "MAIN 类部署（新型）");
        if (testMainDeploy()) {
            passed++;
        }

        println("TC-06", "空路径部署异常");
        if (testInvalidDeploy()) {
            passed++;
        }

        System.out.println();
        println("结果", passed + " / " + total + " 通过");
        return passed == total;
    }

    /**
     * 测试部署指定类型，验证部署单元被正确记录。
     */
    boolean testDeployType(DeployUnitType type, String path) {
        TestWebContainer container = new TestWebContainer("test-" + type.name().toLowerCase());
        try {
            container.initialize(WebContainerSetting.defaults());
            container.deploy(path, "/test", type);
            List<AbstractWebContainer.DeployUnitInfo> units = container.getDeployedUnits();
            boolean found = units.stream().anyMatch(u -> u.getType() == type);
            println("  ->", "部署单元数: " + units.size() + ", 类型匹配: " + found);
            return found;
        } catch (Exception e) {
            println("  !", "异常: " + e.getMessage());
            return false;
        } finally {
            safeStop(container);
        }
    }

    /**
     * 测试 MAIN 类型部署。
     */
    @SuppressWarnings("CallToThreadRun")
    boolean testMainDeploy() {
        mainStarted.set(false);
        TestWebContainer container = new TestWebContainer("test-main");
        try {
            container.initialize(WebContainerSetting.defaults());
            container.deploy(SimpleHttpMain.class.getName(), "/test", DeployUnitType.MAIN);
            boolean started = mainLatch.await(5, TimeUnit.SECONDS);
            println("  ->", "Main 类已启动: " + started);
            List<AbstractWebContainer.DeployUnitInfo> units = container.getDeployedUnits();
            println("  ->", "部署单元数: " + units.size());
            boolean ok = started
                    && units.size() == 1
                    && units.get(0).getType() == DeployUnitType.MAIN;
            return ok;
        } catch (Exception e) {
            println("  !", "异常: " + e.getMessage());
            return false;
        } finally {
            safeStop(container);
        }
    }

    /**
     * 测试空路径部署应抛出异常。
     */
    boolean testInvalidDeploy() {
        TestWebContainer container = new TestWebContainer("test-invalid");
        try {
            container.initialize(WebContainerSetting.defaults());
            container.deploy("", "/test", DeployUnitType.WAR);
            println("  !", "应抛出异常但未抛出");
            return false;
        } catch (WebContainer.ContainerException e) {
            println("  ->", "正确抛出异常: " + e.getMessage());
            return true;
        } finally {
            safeStop(container);
        }
    }

    // ==================== TestWebContainer ====================

    /**
     * AbstractWebContainer 的最小实现，不依赖真实 Servlet 容器。
     */
    @Slf4j
    static class TestWebContainer extends AbstractWebContainer {
        private final String name;

        TestWebContainer(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        protected void doStart() {
        }

        @Override
        protected void doStop() {
        }

        @Override
        protected void doDeploy(String archivePath, String contextPath, DeployUnitType type) {
            log.info("TestWebContainer 部署: type={}, path={}, ctx={}", type, archivePath, contextPath);
        }
    }

    // ==================== Main 测试类 ====================

    /**
     * 简单的 Main 测试类，验证 MAIN 部署类型。
     */
    @SuppressWarnings("CallToThreadRun")
    public static class SimpleHttpMain {
        public static void main(String[] args) {
            mainStarted.set(true);
            mainLatch.countDown();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== 辅助方法 ====================

    static void println(String prefix, String msg) {
        System.out.println(String.format("[NewDeploy] %-5s %s", prefix, msg));
    }

    static void safeStop(WebContainer container) {
        if (container != null && container.isRunning()) {
            try {
                container.stop();
            } catch (Exception ignored) {
            }
        }
    }
}
