package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
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
public class CustomImageProcessor extends JdkImageProcessor implements ImageProcessor {

    @Override
    public String name() {
        return "custom";
    }

    @Override
    public boolean available() {
        return true;
    }
}
