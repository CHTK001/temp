package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.ImageProcessors;
import lombok.extern.slf4j.Slf4j;

/**
 * ImageProcessor API 示例：默认处理器获取与 FluentProcessor 链式 API 存在性自检。
 *
 * <p>改写自 common-starter 测试代码 ImageProcessorApiTest，覆盖场景：
 * SPI 代理可发现默认 ImageProcessor、{@link ImageProcessors#from(byte[])} 可创建
 * FluentProcessor、resize / grayscale / rotate / blur / brightness / crop / flip
 * 七个链式方法签名均存在（仅反射校验签名，不实际处理图像）。</p>
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
            System.out.println("[FAIL] processor-discovery");
            System.exit(1);
        }
        System.out.println("[PASS] processor-discovery name=" + processor.name());

        byte[] placeholder = {0x00, 0x01, 0x02};
        ImageProcessors.FluentProcessor fluent = ImageProcessors.from(placeholder);
        if (fluent == null) {
            System.out.println("[FAIL] fluent-create");
            System.exit(1);
        }
        System.out.println("[PASS] fluent-create");

        if (!hasFluentMethods(fluent)) {
            System.out.println("[FAIL] fluent-chain-methods");
            System.exit(1);
        }
        System.out.println("[PASS] fluent-chain-methods");
    }

    /**
     * 反射校验 FluentProcessor 的七个链式方法签名是否齐全。
     *
     * @param fluent FluentProcessor 实例
     * @return 全部方法存在返回 true
     */
    private static boolean hasFluentMethods(ImageProcessors.FluentProcessor fluent) {
        Class<?> type = fluent.getClass();
        try {
            type.getMethod( "resize", int.class, int.class);
            type.getMethod( "grayscale");
            type.getMethod( "rotate", int.class);
            type.getMethod( "blur", int.class);
            type.getMethod( "brightness", int.class);
            type.getMethod( "crop", int.class, int.class, int.class, int.class);
            type.getMethod( "flip", String.class);
            return true;
        } catch (ReflectiveOperationException e) {
            log.info("  missing method: " + e.getMessage());
            return false;
        }
    }
}
