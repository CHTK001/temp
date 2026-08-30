package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.image.processor.JdkImageProcessor;

/**
 * 自定义 ImageProcessor 子类 — 仅用于 SPI 子类注册验证。
 *
 * <p>不声明 {@code @Spi} 注解，不参与 SPI 自动发现。
 * 仅在测试中手动实例化，验证自定义子类可参与优先级竞争。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class CustomImageProcessorExample extends JdkImageProcessor implements ImageProcessor {

    @Override
    /** Name */
    public String name() {
        return "custom";
    }

    @Override
    /** Available */
    public boolean available() {
        return true;
    }

    public static void main(String[] args) {
        CustomImageProcessorExample processor = new CustomImageProcessorExample();
        log.info("custom processor name={}, available={}", processor.name(), processor.available());
    }

}
