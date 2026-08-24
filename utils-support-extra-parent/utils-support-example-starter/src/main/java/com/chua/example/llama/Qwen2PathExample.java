package com.chua.example.llama;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example: Qwen2PathExample
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class Qwen2PathExample {

    private Qwen2PathExample() {
    }

    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        try {
            // 反射拿 extractRoot/modelRootDir
            var extract = com.chua.deeplearning.support.engine.ModelRegistry.class
                    .getDeclaredField("extractRoot");
            extract.setAccessible(true);
            Object er = extract.get(null);
            log.info("extractRoot = " + er);

            var rootField = com.chua.deeplearning.support.engine.ModelRegistry.class
                    .getDeclaredField("modelRootDir");
            rootField.setAccessible(true);
            log.info("modelRootDir = " + rootField.get(null));

            log.info("java.io.tmpdir = " + System.getProperty("java.io.tmpdir"));
            log.info("cache-dir prop = " + System.getProperty("deeplearning.model.cache-dir"));

            // 缓存文件是否存在
            Path cache = Paths.get("C:/Users/Administrator/AppData/Local/Temp/chua-dl-models/download/qwen2-0.5b/qwen2.5-0.5b-instruct-q4_k_m.gguf");
            log.info("缓存文件存在 = " + Files.exists(cache) + " size=" + (Files.exists(cache) ? Files.size(cache) : 0));

            Path p = ModelRegistry.resolveModelPath("qwen2-0.5b");
            log.info("resolveModelPath = " + p);
            log.info("存在 = " + (p != null && Files.exists(p)));

            var e = ModelRegistry.get("qwen2-0.5b");
            log.info("entry.relPath   = " + (e == null ? "null" : e.relativePath()));
            log.info("entry.dlUrl    = " + (e == null ? "null" : e.downloadUrl()));
            log.info("entry.dlFile   = " + (e == null ? "null" : e.downloadFileName()));

            // 反射调用 tryDownloadFromRemote 观察结果
            var m = ModelRegistry.class.getDeclaredMethod("tryDownloadFromRemote", String.class, Class.forName("com.chua.deeplearning.support.engine.ModelRegistry$Entry"));
            m.setAccessible(true);
            Object dl = m.invoke(null, "qwen2-0.5b", e);
            log.info("tryDownloadFromRemote = " + dl);
        } catch (Exception e) {
            log.info("DIAG FAIL: " + e);
        }
    }
}
