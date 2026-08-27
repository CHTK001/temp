package com.chua.example.ssh;

import com.chua.ssh.support.annotations.ShellMethod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 部署演示命令束，由 SshServer 自动扫描注册。
 *
 * @author CH
 * @since 4.0.0.42
 */
@ShellMethod("/demo")
public class DeployShellCommandsExample {

    /**
     * 问候命令。
     *
     * @param args 参数, 第一个为名字(可选)
     * @return 问候文本
     */
    @ShellMethod(value = "hello", description = "问候: hello [name]")
    public String hello(String[] args) {
        String name = args.length > 0 ? args[0] : "world";
        return "Hello, " + name + "! Greetings from remote SshServer on " + hostname();
    }

    /**
     * 显示服务器当前时间。
     *
     * @param args 参数(不使用)
     * @return 时间字符串
     */
    @ShellMethod(value = "time", description = "显示服务器时间")
    public String time(String[] args) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 显示 JVM 运行信息。
     *
     * @param args 参数(不使用)
     * @return JVM 信息
     */
    @ShellMethod(value = "jvm", description = "显示 JVM 信息")
    public String jvm(String[] args) {
        Runtime rt = Runtime.getRuntime();
        return "java=" + System.getProperty("java.version")
                + ", os=" + System.getProperty("os.name")
                + ", cores=" + rt.availableProcessors()
                + ", memUsed=" + ((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024) + "MB";
    }

    /**
     * 计算器命令。
     *
     * @param args 参数: <num> <op> <num>
     * @return 计算结果
     */
    @ShellMethod(value = "calc", description = "计算器: calc <a> <+|-|*|/> <b>")
    public String calc(String[] args) {
        if (args.length < 3) {
            return "用法: calc <a> <+|-|*|/> <b>";
        }
        try {
            double a = Double.parseDouble(args[0]);
            double b = Double.parseDouble(args[2]);
            return switch (args[1]) {
                case "+" -> String.valueOf(a + b);
                case "-" -> String.valueOf(a - b);
                case "*" -> String.valueOf(a * b);
                case "/" -> b == 0 ? "除数不能为 0" : String.valueOf(a / b);
                default -> "不支持的操作符: " + args[1];
            };
        } catch (NumberFormatException e) {
            return "数字格式错误";
        }
    }

    /** 主机名 */
    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
