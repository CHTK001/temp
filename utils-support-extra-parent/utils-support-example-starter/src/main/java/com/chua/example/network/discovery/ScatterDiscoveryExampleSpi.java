package com.chua.example.network.discovery;

import com.chua.common.support.network.discovery.DefaultServiceDiscovery;
import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.discovery.ServiceDiscoveryServerFilter;
import com.chua.common.support.network.server.filter.proxy.ReverseProxyServer;
import com.chua.common.support.network.server.proxy.DiscoveryProxyTargetResolver;
import com.chua.common.support.network.server.proxy.TcpProxyServer;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;/** Fail */
    private static void ExampleUtils.fail(String msg) {
        log.error("    鉁?澶辫触: {}", msg);
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
