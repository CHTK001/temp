package com.chua.example.media;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * VideoCodecExample SPI 适配器 — 通过反射调用私有 {@code runTest} 方法。
 *
 * <p>由于 {@link VideoCodecExample} 仅暴露 {@code main} 入口且内部大量使用
 * private 静态方法，这里使用反射调用 {@code runTest(String, int, int, int)}，
 * 避免依赖其内部命令行解析路径。参数透传 {@code --type / --width / --height / --fps}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VideoCodecExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "video-codec";
    }

    @Override
    /** Module */
    public String module() {
        return "media";
    }

    @Override
    /** Description */
    public String description() {
        return "视频编解码器自检（javacv-ffmpeg / 屏幕捕获编码器）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String encoderType = args.getOrDefault("type", "javacv-ffmpeg");
        int width = Integer.parseInt(args.getOrDefault("width", "640"));
        int height = Integer.parseInt(args.getOrDefault("height", "480"));
        int fps = Integer.parseInt(args.getOrDefault("fps", "30"));
        try {
            Method m = VideoCodecExample.class.getDeclaredMethod(
                    "runTest", String.class, int.class, int.class, int.class);
            m.setAccessible(true);
            Boolean result = (Boolean) m.invoke(null, encoderType, width, height, fps);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            log.error("video-codec 反射调用失败: {}", t.getMessage(), t);
            return false;
        }
    }
}
