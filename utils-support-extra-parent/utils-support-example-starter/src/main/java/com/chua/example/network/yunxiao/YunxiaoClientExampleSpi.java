package com.chua.example.network.yunxiao;

import com.chua.common.support.network.client.yunxiao.YunxiaoArtifact;
import com.chua.common.support.network.client.yunxiao.YunxiaoClient;
import com.chua.common.support.network.client.yunxiao.YunxiaoClientSetting;
import com.chua.common.support.network.client.yunxiao.YunxiaoDeleteResult;
import com.chua.common.support.network.client.yunxiao.YunxiaoRepository;
import com.chua.common.support.network.client.yunxiao.constant.RepoType;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 云效（Alibaba Cloud DevOps / Yunxiao）OpenAPI 链式客户端示例（SPI 形式）。
 *
 * <p>演示 {@link YunxiaoClient} 的流式 API：查询制品仓库、查询制品列表、查询单个制品、
 * 删除单个制品、删除单个制品版本。所有参数均通过命令行传入，避免在代码中硬编码敏感信息。</p>
 *
 * <h2>参数说明（通过命令行 --key=value 传入）</h2>
 * <table border="1">
 *   <tr><th>参数</th><th>必填</th><th>说明</th><th>示例</th></tr>
 *   <tr><td>--domain</td><td>是</td><td>服务接入点域名（不含协议前缀）</td><td>devops.cn-hangzhou.aliyuncs.com</td></tr>
 *   <tr><td>--token</td><td>是</td><td>个人访问令牌（PAT）</td><td>pt-0fh3****0fbG</td></tr>
 *   <tr><td>--org</td><td>中心版必填</td><td>企业 Id（organizationId）</td><td>60d54f3daccf2bbd6659f3ad</td></tr>
 *   <tr><td>--repo</td><td>是</td><td>制品仓库 Id</td><td>my-repo</td></tr>
 *   <tr><td>--repoType</td><td>否</td><td>仓库类型（默认 MAVEN）</td><td>MAVEN / GENERIC / NPM</td></tr>
 *   <tr><td>--central</td><td>否</td><td>是否为中心版（默认 true）</td><td>true / false</td></tr>
 * </table>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 查询仓库与制品（只读）
 *   java RunnerExample --example=yunxiao --domain=devops.cn-hangzhou.aliyuncs.com \
 *       --token=pt-xxxx --org=60d54f3daccf2bbd6659f3ad --repo=my-repo --repoType=MAVEN
 *
 *   # 演示删除（需读写权限，谨慎使用）
 *   java RunnerExample --example=yunxiao --domain=devops.cn-hangzhou.aliyuncs.com \
 *       --token=pt-xxxx --org=60d54f3daccf2bbd6659f3ad --repo=my-repo --repoType=MAVEN --delete
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YunxiaoClientExampleSpi implements Example {

    /**
     * 仓库类型枚举映射名
     */
    private static final String PARAM_DOMAIN = "domain";

    /**
     * 个人访问令牌参数名
     */
    private static final String PARAM_TOKEN = "token";

    /**
     * 企业 Id 参数名
     */
    private static final String PARAM_ORG = "org";

    /**
     * 仓库 Id 参数名
     */
    private static final String PARAM_REPO = "repo";

    /**
     * 仓库类型参数名
     */
    private static final String PARAM_REPO_TYPE = "repoType";

    /**
     * 中心版开关参数名
     */
    private static final String PARAM_CENTRAL = "central";

    /**
     * 是否演示删除操作参数名（默认不执行删除）
     */
    private static final String PARAM_DELETE = "delete";

    /**
     * 是否演示删除制品版本操作参数名
     */
    private static final String PARAM_DELETE_VERSION = "deleteVersion";

    @Override
    /** Name */
    public String name() {
        return "yunxiao";
    }

    @Override
    /** Module */
    public String module() {
        return "yunxiao";
    }

    @Override
    /** Description */
    public String description() {
        return "云效 OpenAPI 链式客户端（制品仓库 / 制品 / 删除）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String domain = args.get(PARAM_DOMAIN);
        String token = args.get(PARAM_TOKEN);
        String repo = args.get(PARAM_REPO);

        if (domain == null || token == null || repo == null) {
            log.error("缺少必填参数：--domain / --token / --repo（可选：--org / --repoType / --central / --delete / --deleteVersion）");
            return false;
        }

        String org = args.get(PARAM_ORG);
        String repoTypeName = args.getOrDefault(PARAM_REPO_TYPE, "MAVEN");
        boolean central = Boolean.parseBoolean(args.getOrDefault(PARAM_CENTRAL, "true"));

        YunxiaoClientSetting setting = YunxiaoClientSetting.builder()
                .domain(domain)
                .token(token)
                .organizationId(org)
                .central(central)
                .build();

        YunxiaoClient client = YunxiaoClient.create(setting);

        log.info("===== yunxiao --test [domain={}, repo={}, repoType={}, central={}] =====",
                domain, repo, repoTypeName, central);

        boolean passed = true;
        passed &= testListRepositories(client);
        passed &= testListArtifacts(client, repo, repoTypeName);
        passed &= testGetArtifact(client, repo, repoTypeName);

        if (Boolean.parseBoolean(args.getOrDefault(PARAM_DELETE, "false"))) {
            passed &= testDeleteArtifact(client, repo, repoTypeName);
        }

        if (Boolean.parseBoolean(args.getOrDefault(PARAM_DELETE_VERSION, "false"))) {
            passed &= testDeleteArtifactVersion(client, repo, repoTypeName);
        }

        return passed;
    }

    /**
     * 查询制品仓库列表。
     *
     * @param client 云效客户端
     * @return 查询成功返回 {@code true}
     */
    private boolean testListRepositories(YunxiaoClient client) {
        log.info("\n[1] 查询制品仓库列表");
        try {
            List<YunxiaoRepository> repositories = client.repositories().perPage(10).list();
            if (repositories.isEmpty()) {
                log.warn("    未查询到制品仓库");
                return true;
            }
            for (YunxiaoRepository repository : repositories) {
                log.info("    - [{}] {} (type={}, category={})",
                        repository.getRepoId(), repository.getRepoName(),
                        repository.getRepoType(), repository.getRepoCategory());
            }
            return true;
        } catch (Exception e) {
            log.error("查询制品仓库失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 查询指定仓库中的制品列表。
     *
     * @param client      云效客户端
     * @param repo        仓库 Id
     * @param repoTypeName 仓库类型
     * @return 查询成功返回 {@code true}
     */
    private boolean testListArtifacts(YunxiaoClient client, String repo, String repoTypeName) {
        log.info("\n[2] 查询仓库 [{}] 的制品列表", repo);
        try {
            List<YunxiaoArtifact> artifacts = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .perPage(10)
                    .list();
            if (artifacts.isEmpty()) {
                log.warn("    未查询到制品");
                return true;
            }
            for (YunxiaoArtifact artifact : artifacts) {
                int versionCount = artifact.getVersions() == null ? 0 : artifact.getVersions().size();
                log.info("    - [{}] {} (版本数={}, 下载={})",
                        artifact.getId(), artifact.getModule(), versionCount, artifact.getDownloadCount());
            }
            return true;
        } catch (Exception e) {
            log.error("查询制品列表失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 查询单个制品信息。
     *
     * @param client       云效客户端
     * @param repo         仓库 Id
     * @param repoTypeName 仓库类型
     * @return 查询成功返回 {@code true}
     */
    private boolean testGetArtifact(YunxiaoClient client, String repo, String repoTypeName) {
        log.info("\n[3] 查询仓库 [{}] 的单个制品（取列表第一条）", repo);
        try {
            List<YunxiaoArtifact> artifacts = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .perPage(1)
                    .list();
            if (artifacts.isEmpty()) {
                log.warn("    无制品可查询，跳过");
                return true;
            }
            long artifactId = artifacts.get(0).getId();
            YunxiaoArtifact artifact = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .get(artifactId);
            if (artifact == null) {
                log.error("    查询制品 [{}] 失败", artifactId);
                return false;
            }
            log.info("    - 制品 [{}] {}: 组织={}, 最近更新={}",
                    artifact.getId(), artifact.getModule(),
                    artifact.getOrganization(), artifact.getLatestUpdate());
            return true;
        } catch (Exception e) {
            log.error("查询单个制品失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除单个制品（需读写权限）。
     *
     * @param client       云效客户端
     * @param repo         仓库 Id
     * @param repoTypeName 仓库类型
     * @return 删除成功返回 {@code true}
     */
    private boolean testDeleteArtifact(YunxiaoClient client, String repo, String repoTypeName) {
        log.info("\n[4] 删除仓库 [{}] 的单个制品（取列表第一条）", repo);
        try {
            List<YunxiaoArtifact> artifacts = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .perPage(1)
                    .list();
            if (artifacts.isEmpty()) {
                log.warn("    无制品可删除，跳过");
                return true;
            }
            long artifactId = artifacts.get(0).getId();
            YunxiaoDeleteResult result = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .delete(artifactId);
            if (result == null) {
                log.error("    删除制品 [{}] 失败", artifactId);
                return false;
            }
            log.info("    删除任务已提交: action={}, status={}, taskId={}",
                    result.getAction(), result.getStatus(), result.getId());
            return true;
        } catch (Exception e) {
            log.error("删除制品失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除单个制品版本（需读写权限）。
     *
     * @param client       云效客户端
     * @param repo         仓库 Id
     * @param repoTypeName 仓库类型
     * @return 删除成功返回 {@code true}
     */
    private boolean testDeleteArtifactVersion(YunxiaoClient client, String repo, String repoTypeName) {
        log.info("\n[5] 删除仓库 [{}] 的制品版本（取第一条制品的第一版本）", repo);
        try {
            List<YunxiaoArtifact> artifacts = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .perPage(1)
                    .list();
            if (artifacts.isEmpty() || artifacts.get(0).getVersions() == null
                    || artifacts.get(0).getVersions().isEmpty()) {
                log.warn("    无制品版本可删除，跳过");
                return true;
            }
            long artifactId = artifacts.get(0).getId();
            long versionId = artifacts.get(0).getVersions().get(0).getId();
            boolean removed = client.artifacts(repo)
                    .repoType(parseRepoType(repoTypeName))
                    .deleteVersion(artifactId, versionId);
            log.info("    删除制品版本 [{}] / [{}]: {}", artifactId, versionId, removed);
            return removed;
        } catch (Exception e) {
            log.error("删除制品版本失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将仓库类型字符串解析为枚举。
     *
     * @param repoTypeName 仓库类型名称，如 {@code "MAVEN"}
     * @return 仓库类型枚举；无法识别时回退 {@link RepoType#MAVEN}
     */
    private static RepoType parseRepoType(String repoTypeName) {
        try {
            return RepoType.valueOf(repoTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无法识别的仓库类型 [{}]，回退 MAVEN", repoTypeName);
            return RepoType.MAVEN;
        }
    }
}
