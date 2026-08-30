package com.chua.example.network.http;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerAttribute;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.ServerFilterConfig;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.example.network.perf.PerfReportExample;
import com.chua.example.spi.Example;
import com.chua.common.support.utils.ThreadUtils;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;/** Fail */
    private static void ExampleUtils.fail(String msg) {
        log.info("  \u2717 澶辫触: {}", msg);
    }

    /** 鍏抽棴Quietly */
    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
