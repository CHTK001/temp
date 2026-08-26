package com.chua.example.redis;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.redis.support.command.RedisCommandHandler;
import com.chua.redis.support.engine.RedisEngine;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 命令执行示例 — 基于 {@link RedisCommandHandler} SPI 分发。
 *
 * <p>通过 {@link RedisEngine#execute(String, Object...)} 执行 Redis 命令，
 * 命令处理器由 SPI 自动加载（SET / GET / DEL / HSET / EXPIRE / INCR）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认连接 127.0.0.1:6379
 *   java RedisCommandExample
 *
 *   # 指定主机与端口
 *   java RedisCommandExample 172.16.0.40 6379
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class RedisCommandExample {
    private RedisCommandExample() { }


    /**
     * 默认 Redis 主机
     */
    private static final String DEFAULT_HOST = "127.0.0.1";

    /**
     * 默认 Redis 端口
     */
    private static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        // 1) SPI 加载命令处理器
        String[] commands = {"SET", "GET", "DEL", "HSET", "EXPIRE", "INCR"};
        int loaded = 0;
        for (String command : commands) {
            RedisCommandHandler handler = ServiceProvider.of(RedisCommandHandler.class).getExtension(command);
            if (handler != null) {
                loaded++;
            }
        }
        log.info("[Redis] SPI 加载命令处理器 {}/{}", loaded, commands.length);

        RedisEngine engine = new RedisEngine();
        try {
            engine.addDataSource("default", host, port);

            // 2) SET
            String key = "example:user:1";
            int set = engine.execute("SET " + key + " zhangsan");
            log.info("[SET] 键={} 结果={}", key, set);

            // 3) INCR
            engine.execute("SET " + key + ":count 10");
            int incr = engine.execute("INCR " + key + ":count");
            log.info("[INCR] 递增后={}", incr);

            // 4) HSET
            int hset = engine.execute("HSET " + key + ":hash name lisi age 30");
            log.info("[HSET] 结果={}", hset);

            // 5) GET 命中
            int exists = engine.execute("GET " + key);
            log.info("[GET] 命中={}", exists);

            // 6) GET 未命中
            int missing = engine.execute("GET " + key + ":not-exist");
            log.info("[GET] 未命中={}", missing);

            // 7) 清理
            int del = engine.execute("DEL " + key + " " + key + ":count " + key + ":hash");
            log.info("[DEL] 删除={}", del);
        } finally {
            engine.close();
        }
        log.info("[完成] RedisCommandExample 示例执行成功");
    }
}