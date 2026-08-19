package com.chua.example.media;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * ScreenCaptureExample SPI 适配器 — 通过反射调用私有 {@code runTest} 方法。
 *
 * <p>参数：{@code --type=<robot/ffmpeg>} {@code --width=...} {@code --height=...} {@code --fps=...}</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ScreenCaptureExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "screen-capture";
    }

    @Override
    /** Module */
    public String module() {
        return "media";
    }

    @Override
    /** Description */
    public String description() {
        return "屏幕捕获自检（robot / ffmpeg 等实现）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String captureType = args.getOrDefault("type", "robot");
        int width = Integer.parseInt(args.getOrDefault("width", "1920"));
        int height = Integer.parseInt(args.getOrDefault("height", "1080"));
        int fps = Integer.parseInt(args.getOrDefault("fps", "30"));
        try {
            Method m = ScreenCaptureExample.class.getDeclaredMethod(
                    "runTest", String.class, int.class, int.class, int.class);
            m.setAccessible(true);
            Boolean result = (Boolean) m.invoke(null, captureType, width, height, fps);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            log.error("screen-capture 反射调用失败: {}", t.getMessage(), t);
            return false;
        }
    }
}
