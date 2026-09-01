package com.chua.example.network.proxy;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * TCP echo 寮傛鍘嬫祴绔細鍚姩鐩爣 TcpServer锛岀敤 Vert.x NetClient 寮傛 keep-alive 鎵撶湡瀹炲悶鍚愩€? *
 * <p>涓?AsyncServerPerfMain锛圚TTP 寮傛锛夊悓鍙ｅ緞锛堝苟鍙?鏃堕暱/瀹㈡埛绔紓姝ワ級锛屽叕骞冲姣?tcp vs http銆?/p>
 *
 * <p>鐢ㄦ硶锛歿@code java ... AsyncTcpServerPerfMain <type> [durSec] [conc]}锛屽
 * {@code vertx-tcp 5 2000}銆亄@code jdk-tcp 5 2000}銆?/p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AsyncTcpServerPerfExample {

    /** 绉佹湁鏋勯€狅紝闃叉瀹炰緥鍖?*/
    private AsyncTcpServerPerfExample() { }

    /** log */
/** 鍗曟 echo 杞借嵎锛?4B锛屼笌 VertxTcpPerfMain 涓€鑷达級 */
    private static final byte[] PAYLOAD = new byte[64];

    static {
        fill(PAYLOAD, (byte) 'A');
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String type = args.length > 0 ? args[0] : "vertx-tcp";
        int durSec = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int conc = args.length > 2 ? Integer.parseInt(args[2]) : 2000;
        // 姣忚繛鎺ュ苟鍙戞祦鏁帮細涓?HTTP 鍘嬫祴绔?骞跺彂澶?inflight)鍚屽彛寰勩€?        // 璇婃柇缁撹:姝ゅ墠 TCP 鍘嬫祴姣忚繛鎺?1 inflight 涓茶,鍙?RTT 闄愬埗鍗曡繛鎺ュ悶鍚?
        // 杩滀綆浜?HTTP 姣忚繛鎺ュ苟鍙戝娴?姝ゅ姣忚繛鎺ュ苟鍙?streams 涓湪閫?echo 鎷夊钩鍙ｅ緞銆?        int streams = args.length > 3 ? Integer.parseInt(args[3]) : 8;

        // 1. 鍚姩鐩爣 TcpServer锛堥」鐩嚜宸辩殑瀹炵幇锛?        Server server = ServerBuilder.create().type(type).host("127.0.0.1").port(0).build();
        server.start();
        int port = server.getPort();
        log.info("[AsyncTcpPerf] 鏈嶅姟鍣ㄥ凡鍚姩: type={}, port={}, 寮€濮嬪紓姝ュ帇娴?dur={}s conc={} streams={}",
                type, port, durSec, conc, streams);

        // 2. 寮傛鍘嬫祴绔細Vert.x NetClient锛屾瘡杩炴帴 keep-alive 澶嶇敤锛屽苟鍙戝洖鏄?        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 2))
                .setPreferNativeTransport(true));
        NetClient client = vertx.createNetClient(new NetClientOptions()
                .setTcpNoDelay(true)
                .setConnectTimeout(5000)
                .setReconnectAttempts(0));

        LongAdder ok = new LongAdder();
        LongAdder errors = new LongAdder();
        Semaphore inflight = new Semaphore(conc);
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch allDone = new CountDownLatch(1);
        long t0 = System.nanoTime();
        long deadline = System.nanoTime() + (long) durSec * 1_000_000_000L;

        // 閫掑綊鍙戦€侊細涓€涓?echo 瀹屾垚 鈫?鑻ユ湭鍒版湡闄愮户缁彂涓嬩竴涓紙杩炴帴鐢?Vert.x 姹犲寲澶嶇敤锛?        java.util.function.BiConsumer<NetSocket, Buffer> fire = new java.util.function.BiConsumer<>() {
            @Override
            /** Accept */
            public void accept(NetSocket socket, Buffer buf) {
                socket.write(buf).onComplete(ar -> {
                    if (ar.succeeded()) {
                        ok.increment();
                    } else {
                        errors.increment();
                    }
                    inflight.release();
                    next(socket, buf);
                });
            }
        };

        // 寤虹珛 conc 涓繛鎺ワ紝姣忚繛鎺ュ苟鍙?streams 涓湪閫?echo锛堜笌 HTTP 鍘嬫祴绔苟鍙戝娴佸悓鍙ｅ緞锛?        for (int i = 0; i < conc; i++) {
            try {
                inflight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            client.connect(port, "127.0.0.1").onSuccess(socket -> {
                socket.handler(data -> {
                    // 鍝嶅簲鍒拌揪锛氳鏁板悗琛ュ彂涓€涓紝缁存寔姣忚繛鎺?streams 涓湪閫?                    ok.increment();
                    inflight.release();
                    next(socket, Buffer.buffer(PAYLOAD));
                }).exceptionHandler(err -> {
                    errors.increment();
                    inflight.release();
                });
                // 姣忚繛鎺ラ鍙?streams 涓?echo锛堝苟鍙戝湪閫旓級
                for (int s = 0; s < streams; s++) {
                    socket.write(Buffer.buffer(PAYLOAD));
                }
            }).onFailure(err -> {
                errors.increment();
                inflight.release();
            });
        }
        boolean finished = allDone.await(durSec + 10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long total = ok.sum();
        long errs = errors.sum();
        double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
        log.info("[AsyncTcpPerf] {} 鍚炲悙: {} 璇锋眰/{}ms = {} req/s, ok={}, errors={}, finished={}",
                type, total, elapsedMs, Math.round(rps), total, errs, finished);
        client.close();
        vertx.close();
        server.stop();
    }

    /** Next */
    private static void next(NetSocket socket, Buffer buf) {
        // 杩炴帴宸插叧鏃?write 浼?fail锛岀敱 exceptionHandler 鍏滃簳璁℃暟锛屾澶勭洿鎺ュ啓
        if (socket != null) {
            socket.write(buf);
        }
    }
}
