package com.chua.example.arcsoft;

import lombok.extern.slf4j.Slf4j;

/**
 * ArcSoft native classloader 自检示例。
 * <p>
 * 用于排查 native 库（libarcsoft_face.dll 等）资源路径问题：
 * 分别打印上下文类加载器与当前类加载器的资源定位结果，
 * 定位 classpath 前缀斜杠差异导致的资源找不到问题。
 *
 * <p>参数（{@code --key=value} 或 {@code --key value}）：
 * <ul>
 *   <li>{@code --native=path}：待探测的 native 资源路径（缺省 arcsoft dll）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public final class TestClassLoaderExample {

    /**
     * 默认探测的 native 资源目录。
     */
    private static final String DEFAULT_NATIVE_DIR = "native/windows-x86_64/";

    /**
     * 默认探测的 dll 文件。
     */
    private static final String DEFAULT_NATIVE_DLL = "native/windows-x86_64/libarcsoft_face.dll";

    private TestClassLoaderExample() {
    }

    public static void main(String[] args) {
        String nativeDir = DEFAULT_NATIVE_DIR;
        String nativeDll = DEFAULT_NATIVE_DLL;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--native=")) {
                String v = arg.substring("--native=".length());
                nativeDir = v.endsWith("/") ? v : v + "/";
                nativeDll = v;
            } else if (arg.equals("--native") && i + 1 < args.length) {
                String v = args[++i];
                nativeDir = v.endsWith("/") ? v : v + "/";
                nativeDll = v;
            }
        }

        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        log.info("ContextClassLoader: {}", contextCl);
        log.info("Context resource: {}", contextCl.getResource(nativeDir));
        log.info("Context resource (slash): {}", contextCl.getResource("/" + nativeDir));

        ClassLoader myCl = TestClassLoaderExample.class.getClassLoader();
        log.info("MyClassLoader: {}", myCl);
        log.info("My resource: {}", myCl.getResource(nativeDir));
        log.info("My resource (slash): {}", myCl.getResource("/" + nativeDir));

        Object url = myCl.getResource(nativeDll);
        log.info("File resource: {}", url);
    }
}
