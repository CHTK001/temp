package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.ImageProcessors;
import lombok.extern.slf4j.Slf4j;

/**
 * ImageProcessor API 示例：默认处理器获取与 FluentProcessor 链式 API 自检。
 *
 * <p>改写自 common-starter 测试代码 ImageProcessorApiTest，覆盖场景：
 * SPI 代理可发现默认 ImageProcessor、{@link ImageProcessors#from(byte[])} 可创建
 * FluentProcessor、resize / grayscale / rotate / blur / brightness / crop / flip
 * 七个链式方法可正常调用并产出结果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ImageProcessorApiExample {

    /** 私有构造，防止实例化 */
    private ImageProcessorApiExample() {
    }

    /**
     * 入口：依次执行三组 API 自检，任一失败即打印 [FAIL] 并以退出码 1 结束。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        ImageProcessor processor = ImageProcessors.getProcessor();
        if (processor == null) {
            log.info("[FAIL] processor-discovery");
            System.exit(1);
        }
        log.info("[PASS] processor-discovery name=" + processor.name());

        byte[] placeholder = {0x00, 0x01, 0x02};
        ImageProcessors.FluentProcessor fluent = ImageProcessors.from(placeholder);
        if (fluent == null) {
            log.info("[FAIL] fluent-create");
            System.exit(1);
        }
        log.info("[PASS] fluent-create");

        if (!verifyFluentChain(fluent)) {
            log.info("[FAIL] fluent-chain-methods");
            System.exit(1);
        }
        log.info("[PASS] fluent-chain-methods");
    }

    /**
     * 实际调用 FluentProcessor 的七个链式方法，验证链式 API 可正常使用。
     *
     * <p>方法签名由 {@link ImageProcessors.FluentProcessor} 编译期固定，
     * 无需反射检查存在性，直接链式调用即可验证。</p>
     *
     * @param fluent FluentProcessor 实例
     * @return 链式调用不抛异常返回 true
     */
    private static boolean verifyFluentChain(ImageProcessors.FluentProcessor fluent) {
        try {
            fluent.resize(64, 64)
                    .grayscale()
                    .rotate(90)
                    .blur(2)
                    .brightness(10)
                    .crop(0, 0, 64, 64)
                    .flip("horizontal");
            return true;
        } catch (RuntimeException e) {
            log.info("  fluent-chain 调用异常: " + e.getMessage());
            return false;
        }
    }
}
