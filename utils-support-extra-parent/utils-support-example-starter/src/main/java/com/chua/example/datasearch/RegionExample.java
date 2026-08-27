package com.chua.example.datasearch;

import com.chua.common.support.datasearch.region.model.RegionInfo;
import com.chua.common.support.datasearch.region.spi.RegionProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 行政区划检索演示。
 *
 * <p>遍历指定或全部 {@link RegionProvider} SPI 实现，依次执行：
 * <ul>
 *   <li>{@code getRegions()} — 获取默认层级扁平列表</li>
 *   <li>{@code getRegions(2)}  — 省级+市级两级列表</li>
 *   <li>{@code getChildren(parent)} — 获取指定父级下的子节点（默认查省级）</li>
 *   <li>{@code getTree(2)}      — 获取两级行政区划树</li>
 * </ul>
 *
 * <p>参数格式 {@code --key=value} 或 {@code --key value}：</p>
 * <ul>
 *   <li>{@code --spi=alibaba} 指定实现名称，默认遍历全部</li>
 *   <li>{@code --level=3} 指定 tree 查询层级，默认 2</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class RegionExample {

    /** 遍历全部实现的参数值 */
    private static final String SPI_ALL = "all";

    /** 参数前缀 */
    private static final String PARAM_PREFIX = "--";

    /** 默认 tree 层级 */
    private static final int DEFAULT_TREE_LEVEL = 2;

    private RegionExample() {
    }

    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);
        String spiName = params.getOrDefault("spi", SPI_ALL);
        int treeLevel = Integer.parseInt(params.getOrDefault("level", String.valueOf(DEFAULT_TREE_LEVEL)));
        Map<String, RegionProvider> providers = resolveProviders(spiName);
        if (providers.isEmpty()) {
            System.out.println("[FAIL] 未找到任何 RegionProvider 实现: spi=" + spiName);
            System.exit(1);
        }
        int failures = 0;
        for (Map.Entry<String, RegionProvider> entry : providers.entrySet()) {
            if (!runScenario(entry.getKey(), entry.getValue(), treeLevel)) {
                failures++;
            }
        }
        if (failures > 0) {
            log.info("[FAIL] 失败实现数: " + failures);
            System.exit(1);
        }
        log.info("[PASS] 全部通过, 共 " + providers.size() + " 个实现");
        System.exit(0);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith(PARAM_PREFIX)) {
                continue;
            }
            int idx = arg.indexOf('=');
            if (idx > PARAM_PREFIX.length()) {
                params.put(arg.substring(PARAM_PREFIX.length(), idx), arg.substring(idx + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith(PARAM_PREFIX)) {
                params.put(arg.substring(PARAM_PREFIX.length()), args[++i]);
            }
        }
        return params;
    }

    private static Map<String, RegionProvider> resolveProviders(String spiName) {
        ServiceProvider<RegionProvider> provider = ServiceProvider.of(RegionProvider.class);
        if (SPI_ALL.equalsIgnoreCase(spiName)) {
            return provider.list();
        }
        Map<String, RegionProvider> result = new LinkedHashMap<>(2);
        RegionProvider impl = provider.getExtension(spiName);
        if (impl != null) {
            result.put(spiName, impl);
        }
        return result;
    }

    private static boolean runScenario(String name, RegionProvider provider, int treeLevel) {
        try {
            // getRegions() — 默认层级
            List<RegionInfo> regions = provider.getRegions();
            if (CollectionUtils.isEmpty(regions)) {
                System.out.println("[FAIL] " + name + " getRegions() 返回空列表");
                return false;
            }
            System.out.printf("[PASS] %-12s getRegions() rows=%d%n", name, regions.size());

            // getRegions(2) — 两级扁平列表
            List<RegionInfo> level2 = provider.getRegions(2);
            if (CollectionUtils.isEmpty(level2)) {
                System.out.println("[FAIL] " + name + " getRegions(2) 返回空列表");
                return false;
            }
            System.out.printf("[PASS] %-12s getRegions(2) rows=%d%n", name, level2.size());

            // getChildren(null) — 省级列表
            List<RegionInfo> provinces = provider.getChildren(null);
            if (CollectionUtils.isEmpty(provinces)) {
                System.out.println("[FAIL] " + name + " getChildren(null) 返回空列表");
                return false;
            }
            System.out.printf("[PASS] %-12s getChildren(省级) rows=%d%n", name, provinces.size());

            // getChildren(省级 adcode) — 市级列表
            String firstProvinceCode = provinces.get(0).getAdcode();
            List<RegionInfo> cities = provider.getChildren(firstProvinceCode);
            if (CollectionUtils.isEmpty(cities)) {
                System.out.println("[FAIL] " + name + " getChildren(" + firstProvinceCode + ") 返回空列表");
                return false;
            }
            System.out.printf("[PASS] %-12s getChildren(%s) rows=%d%n", name, firstProvinceCode, cities.size());

            // getTree(level) — 两级树
            RegionInfo tree = provider.getTree(treeLevel);
            if (tree == null || !("中国".equals(tree.getName()))) {
                System.out.println("[FAIL] " + name + " getTree root 异常");
                return false;
            }
            int treeNodeCount = countTreeNodes(tree);
            if (treeNodeCount <= 1) {
                System.out.println("[FAIL] " + name + " getTree 节点数不足: " + treeNodeCount);
                return false;
            }
            System.out.printf("[PASS] %-12s getTree(level=%d) nodes=%d%n", name, treeLevel, treeNodeCount);

            // 抽查节点属性完整性
            RegionInfo sample = regions.get(0);
            assertSample(sample);

            return true;
        } catch (Exception e) {
            System.out.printf("[FAIL] %-12s exception: %s%n", name, e.getMessage());
            return false;
        }
    }

    /**
     * 统计树中所有节点数（含根节点）。
     *
     * @param root 根节点
     * @return 节点总数
     */
    private static int countTreeNodes(RegionInfo root) {
        int count = 1;
        List<RegionInfo> children = root.getChildren();
        if (children != null) {
            for (RegionInfo child : children) {
                count += countTreeNodes(child);
            }
        }
        return count;
    }

    /**
     * 断言节点关键字段非空。
     *
     * @param region 样本节点
     */
    private static void assertSample(RegionInfo region) {
        assert region.getAdcode() != null && !region.getAdcode().isEmpty() : "adcode 为空";
        assert region.getName() != null && !region.getName().isEmpty() : "name 为空";
        assert region.getLevel() > 0 : "level 为 0";
    }
}
