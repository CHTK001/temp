package com.chua.example.network.rpc;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.example.spi.Example;
import com.chua.common.support.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;/**
     * 杈撳嚭澶辫触鏃ュ織銆?     *
     * @param msg 澶辫触鎻忚堪
     */
    private static void ExampleUtils.fail(String msg) {
        log.info("  鉁?澶辫触: {}", msg);
    }

    /**
     * 闈欓粯鍏抽棴 RPC 瀹㈡埛绔€?     *
     * @param client 瀹㈡埛绔疄渚嬶紝鍙负 {@code null}
     */
    private static void closeQuietly(RpcClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }
    }

    /**
     * 闈欓粯鍏抽棴 RPC 鏈嶅姟绔€?     *
     * @param server 鏈嶅姟绔疄渚嬶紝鍙负 {@code null}
     */
    private static void closeQuietly(RpcServer server) {
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }
    }

    /**
     * 鐙珛鍏ュ彛锛氭敮鎸?--type=native|json|dubbo|sofa|bench 鍙傛暟銆?     */
    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        boolean passed = new RpcExample().run(parsed);
        log.info("[RpcExample] type={}, passed={}", parsed.get("type"), passed);
        System.exit(passed ? 0 : 1);
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    result.put(arg.substring(2, eq), arg.substring(eq + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    result.put(arg.substring(2), args[++i]);
                }
            }
        }
        return result;
    }
}
