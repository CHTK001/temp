package com.chua.example.network.yunxiao;

import com.chua.common.support.network.client.yunxiao.YunxiaoClient;
import com.chua.common.support.network.client.yunxiao.YunxiaoClientSetting;
import com.chua.common.support.network.client.yunxiao.constant.RepoType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 云效 OpenAPI 链式客户端主示例（驱动型）。
 *
 * <p>默认走本地可验证部分（配置构建回读、客户端工厂、仓库类型解析），不访问外部云效服务，
 * 并输出 {@code [SKIP] remote-disabled}；追加 {@code --remote} 且携带
 * {@code --domain/--token/--repo} 后方才驱动 {@link YunxiaoClientExampleSpi#run(Map)} 访问远端。
 * 令牌一律经命令行传入，代码不硬编码任何 token。</p>
 *
 * <p>用法：</p>
 * <pre>
 *   # 本地自检（无网络）
 *   java ... YunxiaoClientExample
 *   # 远端只读查询（token 由调用方提供）
 *   java ... YunxiaoClientExample --remote --domain=devops.cn-hangzhou.aliyuncs.com \
 *       --token=pt-xxxx --org=60d54f... --repo=my-repo [--repoType=MAVEN] [--central=true]
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YunxiaoClientExample {
    private YunxiaoClientExample() { }


    /** 远端开关参数名。 */
    private static final String PARAM_REMOTE = "remote";
    /** 服务接入点域名参数名。 */
    private static final String PARAM_DOMAIN = "domain";
    /** 个人访问令牌参数名。 */
    private static final String PARAM_TOKEN = "token";
    /** 仓库 Id 参数名。 */
    private static final String PARAM_REPO = "repo";

    /**
     * 主入口：默认本地自检 + [SKIP] remote-disabled；--remote 且凭证齐全时走远端链路。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        String remoteFlag = parsed.get(PARAM_REMOTE);
        boolean passed;
        String scene;
        if (remoteFlag == null || "false".equalsIgnoreCase(remoteFlag)) {
            log.info("[SKIP] remote-disabled（追加 --remote 及 --domain/--token/--repo 后访问远端）");
            passed = runLocalChecks();
            scene = "本地自检(setting/client/repoType)";
        } else if (missing(parsed, PARAM_DOMAIN) || missing(parsed, PARAM_TOKEN)
                || missing(parsed, PARAM_REPO)) {
            log.error("--remote 模式缺少必填参数：--domain / --token / --repo");
            passed = false;
            scene = "远端参数校验";
        } else {
            passed = new YunxiaoClientExampleSpi().run(parsed);
            scene = "远端链路";
        }
        if (!passed) {
            log.info("[FAIL] yunxiao " + scene);
            System.exit(1);
        }
        log.info("[PASS] yunxiao " + scene);
    }

    /**
     * 本地可验证部分：配置构建与字段回读、客户端工厂创建、仓库类型解析与非法回退，全程无网络访问。
     *
     * @return 全部通过返回 {@code true}
     */
    private static boolean runLocalChecks() {
        log.info("[YUNXIAO-LOCAL] 配置构建 + 客户端工厂 + 仓库类型解析（无网络访问）");
        try {
            YunxiaoClientSetting setting = YunxiaoClientSetting.builder()
                    .domain("devops.cn-hangzhou.aliyuncs.com")
                    .organizationId("org-local-selfcheck")
                    .central(true)
                    .build();
            boolean settingOk = "devops.cn-hangzhou.aliyuncs.com".equals(setting.getDomain())
                    && setting.getToken() == null
                    && "org-local-selfcheck".equals(setting.getOrganizationId())
                    && setting.isCentral();
            check("setting 构建字段回读", settingOk);
            check("client 工厂创建", YunxiaoClient.create(setting) != null);
            check("repoType 合法解析", RepoType.valueOf("MAVEN") == RepoType.MAVEN);
            check("repoType 非法回退 MAVEN", fallbackRepoType("NOSUCH") == RepoType.MAVEN);
            return true;
        } catch (Exception e) {
            log.error("本地自检异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 单项断言，未通过即抛出终止后续检查。
     *
     * @param scene 检查项描述
     * @param ok    断言结果
     */
    private static void check(String scene, boolean ok) {
        if (ok) {
            log.info("  [OK] {}", scene);
            return;
        }
        throw new IllegalStateException("本地自检未通过: " + scene);
    }

    /**
     * 仓库类型解析，无法识别时回退 {@link RepoType#MAVEN}（对齐 Spi 内 parseRepoType 语义）。
     *
     * @param repoTypeName 仓库类型名称
     * @return 仓库类型枚举
     */
    private static RepoType fallbackRepoType(String repoTypeName) {
        try {
            return RepoType.valueOf(repoTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RepoType.MAVEN;
        }
    }

    /**
     * 判断参数缺失（null 或空串）。
     *
     * @param parsed 参数表
     * @param key    参数名
     * @return 缺失返回 {@code true}
     */
    private static boolean missing(Map<String, String> parsed, String key) {
        String value = parsed.get(key);
        return value == null || value.isEmpty();
    }

    /**
     * 解析 {@code --key=value} 与 {@code --key value} 两种形式；无值开关记为空串。
     *
     * @param args 原生命令行参数
     * @return 键值参数表
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String kv = arg.substring(2);
            int eq = kv.indexOf('=');
            if (eq > 0) {
                map.put(kv.substring(0, eq), kv.substring(eq + 1));
                continue;
            }
            String next = i + 1 < args.length ? args[i + 1] : null;
            if (next != null && !next.startsWith("--")) {
                map.put(kv, next);
                i++;
                continue;
            }
            map.put(kv, "");
        }
        return map;
    }
}
