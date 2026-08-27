package com.chua.example.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 引擎示例通用测试基座：集中管理失败计数、断言输出与临时目录生命周期，
 * 避免各 Example 重复定义 private 辅助方法。
 *
 * <p>子类只需实现场景方法（参数解析 → 调用正式 API → 通过 {@link #ck} 断言），
 * 并在 {@code main} 中按 {@code --mode} 分发执行。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public abstract class AbstractEngineExample {

    /** 日志，子类直接复用 */
    protected static final Logger log = LoggerFactory.getLogger(AbstractEngineExample.class);

    /** 失败计数（线程安全） */
    protected static final AtomicInteger FAILED = new AtomicInteger();

    /**
     * 断言场景结果，输出统一 {@code [PASS]}/{@code [FAIL]} 标记。
     *
     * @param scene    场景名
     * @param expected 期望结果
     * @param actual   实际结果
     */
    protected static void ck(String scene, Object expected, Object actual) {
        boolean pass = expected == null ? actual == null : expected.equals(actual);
        log.info("{} {} => {}", pass ? "[PASS]" : "[FAIL]", scene,
                pass ? actual : "expected=" + expected + " actual=" + actual);
        if (!pass) {
            FAILED.incrementAndGet();
        }
    }

    /**
     * 创建统一测试输出目录（位于系统临时目录下的 {@code test-output/}）。
     *
     * @param name 子目录名
     * @return 目录路径，创建失败时返回 null
     */
    protected static Path tempDir(String name) {
        try {
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "test-output", name);
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            FAILED.incrementAndGet();
            log.info("[FAIL] 创建临时目录 {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * 递归清理临时目录及其内容。
     *
     * @param dir 待清理目录
     */
    protected static void cleanup(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 忽略个别文件删除失败
                        }
                    });
        } catch (IOException ignored) {
            // 忽略删除异常
        }
    }
}
