package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.processor.JdkImageProcessor;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiOrder;

/**
 * 自定义 ImageProcessor 子类 — 验证 SPI 子类注册与优先级竞争。
 *
 * <p>继承 {@link JdkImageProcessor} 复用其 AWT 实现，仅覆盖
 * {@link #name()} / {@link #available()} 并声明最高优先级
 * {@code @SpiOrder(200)}，用于演示：</p>
 * <ul>
 *   <li>SPI 自动发现自定义子类（无需改框架代码）</li>
 *   <li>子类可通过 {@code @SpiOrder} 参与优先级排序（200 &gt; rust 100 &gt; opencv 50 &gt; jdk -100）</li>
 *   <li>子类实现与既有实现共存、按优先级自动降级</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi("image-processor")
@SpiOrder(200)
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
