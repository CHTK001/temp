package com.chua.example.utils;

import com.chua.common.support.utils.TreeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link TreeUtils} 全场景自检示例。
 *
 * <p>覆盖平铺转树（含孤儿归根/重复 ID 拒绝/循环引用检测）、按 ID 与条件查找、
 * 直接子节点与全部后代、父链与完整路径、兄弟节点、递归排序、前序扁平化与深度计算。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java TreeUtilsExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class TreeUtilsExample {

    /**
     * 防止实例化工具类。
     */
    private TreeUtilsExample() {
    }

    /**
     * 示例节点模型：ID / 父 ID / 子节点。
     */
    public static final class Node {
        /** 节点 ID */
        public final String id;
        /** 父节点 ID */
        public final String parentId;
        /** 子节点列表 */
        public List<Node> children = new ArrayList<>();

        /**
         * 构造节点。
         *
         * @param id       节点 ID
         * @param parentId 父节点 ID，根传 null
         */
        public Node(String id, String parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        /** 返回 ID 便于断言输出。 */
        @Override
        public String toString() {
            return id;
        }
    }

    /**
     * 构造标准测试树：
     * <pre>
     * 1
     * ├── 2
     * │   ├── 4
     * │   └── 5
     * └── 3
     *      └── 6
     * </pre>
     *
     * @return 平铺节点列表
     */
    private static List<Node> sampleFlat() {
        var list = new ArrayList<Node>();
        list.add(new Node("1", null));
        list.add(new Node("2", "1"));
        list.add(new Node("3", "1"));
        list.add(new Node("4", "2"));
        list.add(new Node("5", "2"));
        list.add(new Node("6", "3"));
        return list;
    }

    /**
     * ID 访问器。
     */
    private static java.util.function.Function<Node, String> idOf() {
        return n -> n.id;
    }

    /**
     * 父 ID 访问器。
     */
    private static java.util.function.Function<Node, String> pidOf() {
        return n -> n.parentId;
    }

    /**
     * 子节点访问器。
     */
    private static java.util.function.Function<Node, List<Node>> kidsOf() {
        return n -> n.children;
    }

    // ==================== main ====================

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        var passed = true;
        passed &= ExampleUtils.timed("buildNormalTree", TreeUtilsExample::buildNormalTree);
        passed &= ExampleUtils.timed("buildOrphanBecomesRoot", TreeUtilsExample::buildOrphanBecomesRoot);
        passed &= ExampleUtils.timed("buildRejectsDuplicateId", TreeUtilsExample::buildRejectsDuplicateId);
        passed &= ExampleUtils.timed("buildDetectsCycle", TreeUtilsExample::buildDetectsCycle);
        passed &= ExampleUtils.timed("findByIdAndFindNode", TreeUtilsExample::findByIdAndFindNode);
        passed &= ExampleUtils.timed("directAndAllChildren", TreeUtilsExample::directAndAllChildren);
        passed &= ExampleUtils.timed("parentChainAndParents", TreeUtilsExample::parentChainAndParents);
        passed &= ExampleUtils.timed("siblingsExcludeSelf", TreeUtilsExample::siblingsExcludeSelf);
        passed &= ExampleUtils.timed("rootSiblingsAreOtherRoots", TreeUtilsExample::rootSiblingsAreOtherRoots);
        passed &= ExampleUtils.timed("sortTreeRecursive", TreeUtilsExample::sortTreeRecursive);
        passed &= ExampleUtils.timed("flattenPreOrder", TreeUtilsExample::flattenPreOrder);
        passed &= ExampleUtils.timed("depthCalculation", TreeUtilsExample::depthCalculation);
        if (!passed) {
            log.info("[FAIL] TreeUtils 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        log.info("[PASS] TreeUtils 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }

    // ==================== 场景 ====================

    /**
     * 场景：正常多级构建 — 根识别、子节点挂载与层内顺序正确。
     *
     * @return true 表示通过
     */
    private static boolean buildNormalTree() {
        try {
            List<Node> roots = TreeUtils.build(sampleFlat(), idOf(), pidOf(),
                    (n, kids) -> n.children = kids);
            var ok = roots.size() == 1
                    && "1".equals(roots.getFirst().id)
                    && roots.getFirst().children.size() == 2
                    && "2".equals(roots.getFirst().children.get(0).id)
                    && "3".equals(roots.getFirst().children.get(1).id)
                    && roots.getFirst().children.get(0).children.size() == 2;
            ExampleUtils.print("buildNormalTree", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("buildNormalTree", e);
        }
    }

    /**
     * 场景：孤儿节点（父 ID 不存在）自动归为根。
     *
     * @return true 表示通过
     */
    private static boolean buildOrphanBecomesRoot() {
        try {
            var flat = new ArrayList<Node>();
            flat.add(new Node("r", null));
            flat.add(new Node("x", "missing-parent"));
            List<Node> roots = TreeUtils.build(flat, idOf(), pidOf(),
                    (n, kids) -> n.children = kids);
            var ok = roots.size() == 2;
            ExampleUtils.print("buildOrphanBecomesRoot", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("buildOrphanBecomesRoot", e);
        }
    }

    /**
     * 场景：重复 ID 构建期拒绝。
     *
     * @return true 表示通过
     */
    private static boolean buildRejectsDuplicateId() {
        try {
            var flat = new ArrayList<Node>();
            flat.add(new Node("dup", null));
            flat.add(new Node("dup", null));
            TreeUtils.build(flat, idOf(), pidOf(), (n, kids) -> n.children = kids);
            return ExampleUtils.fail("buildRejectsDuplicateId", "未拒绝重复 ID");
        } catch (IllegalArgumentException expected) {
            boolean ok = expected.getMessage().contains("重复");
            ExampleUtils.print("buildRejectsDuplicateId", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("buildRejectsDuplicateId", e);
        }
    }

    /**
     * 场景：循环引用构建期检测（b 的父是 c，c 的父是 b）。
     *
     * @return true 表示通过
     */
    private static boolean buildDetectsCycle() {
        try {
            var flat = new ArrayList<Node>();
            flat.add(new Node("a", null));
            flat.add(new Node("b", "c"));
            flat.add(new Node("c", "b"));
            TreeUtils.build(flat, idOf(), pidOf(), (n, kids) -> n.children = kids);
            return ExampleUtils.fail("buildDetectsCycle", "未检测到循环引用");
        } catch (IllegalStateException expected) {
            boolean ok = expected.getMessage().contains("循环引用");
            ExampleUtils.print("buildDetectsCycle", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("buildDetectsCycle", e);
        }
    }

    /**
     * 场景：findById 命中深层节点 / 未命中返回 null；findNode 条件匹配首个命中。
     *
     * @return true 表示通过
     */
    private static boolean findByIdAndFindNode() {
        try {
            List<Node> roots = buildSampleRoots();
            Node hitById = TreeUtils.findById(roots, "5", idOf(), kidsOf());
            Node miss = TreeUtils.findById(roots, "nope", idOf(), kidsOf());
            Node firstWithoutKids = TreeUtils.findNode(roots,
                    n -> n.children.isEmpty(), kidsOf());
            boolean ok = hitById != null && "5".equals(hitById.id)
                    && miss == null
                    && firstWithoutKids != null && "4".equals(firstWithoutKids.id);
            ExampleUtils.print("findByIdAndFindNode", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("findByIdAndFindNode", e);
        }
    }

    /**
     * 场景：直接子节点与全部后代。
     *
     * @return true 表示通过
     */
    private static boolean directAndAllChildren() {
        try {
            List<Node> roots = buildSampleRoots();
            Node root1 = TreeUtils.findById(roots, "1", idOf(), kidsOf());
            Node node2 = TreeUtils.findById(roots, "2", idOf(), kidsOf());
            List<Node> direct = TreeUtils.getDirectChildren(root1, kidsOf());
            List<Node> all = TreeUtils.getAllChildren(root1, kidsOf());
            List<Node> leafDescendants = TreeUtils.getAllChildren(node2, kidsOf());
            var ok = direct.size() == 2
                    && all.size() == 5
                    && leafDescendants.size() == 2;
            ExampleUtils.print("directAndAllChildren", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("directAndAllChildren", e);
        }
    }

    /**
     * 场景：getParentChain 为根→...→目标的完整路径；getParents 为直接父→根倒序。
     *
     * @return true 表示通过
     */
    private static boolean parentChainAndParents() {
        try {
            List<Node> roots = buildSampleRoots();
            Node node5 = TreeUtils.findById(roots, "5", idOf(), kidsOf());
            List<Node> chain = TreeUtils.getParentChain(roots, node5, kidsOf());
            List<Node> parents = TreeUtils.getParents(roots, node5, kidsOf());
            var chainIds = chain.stream().map(n -> n.id).toList();
            var parentIds = parents.stream().map(n -> n.id).toList();
            var ok = chainIds.equals(List.of("1", "2", "5"))
                    && parentIds.equals(List.of("2", "1"))
                    && TreeUtils.getParents(roots, roots.getFirst(), kidsOf()).isEmpty();
            ExampleUtils.print("parentChainAndParents", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("parentChainAndParents", e);
        }
    }

    /**
     * 场景：兄弟节点排除自身。
     *
     * @return true 表示通过
     */
    private static boolean siblingsExcludeSelf() {
        try {
            List<Node> roots = buildSampleRoots();
            Node node5 = TreeUtils.findById(roots, "5", idOf(), kidsOf());
            List<Node> siblings = TreeUtils.getSiblings(roots, node5, kidsOf());
            var ids = siblings.stream().map(n -> n.id).toList();
            var ok = ids.equals(List.of("4"));
            ExampleUtils.print("siblingsExcludeSelf", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("siblingsExcludeSelf", e);
        }
    }

    /**
     * 场景：根节点的兄弟即其余根节点。
     *
     * @return true 表示通过
     */
    private static boolean rootSiblingsAreOtherRoots() {
        try {
            var flat = new ArrayList<Node>();
            flat.add(new Node("r1", null));
            flat.add(new Node("r2", null));
            List<Node> roots = TreeUtils.build(flat, idOf(), pidOf(),
                    (n, kids) -> n.children = kids);
            List<Node> siblings = TreeUtils.getSiblings(roots, roots.getFirst(), kidsOf());
            var ids = siblings.stream().map(n -> n.id).toList();
            var ok = ids.equals(List.of("r2"));
            ExampleUtils.print("rootSiblingsAreOtherRoots", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("rootSiblingsAreOtherRoots", e);
        }
    }

    /**
     * 场景：sortTree 递归排序每一层（倒序比较器后全部反转）。
     *
     * @return true 表示通过
     */
    private static boolean sortTreeRecursive() {
        try {
            List<Node> roots = buildSampleRoots();
            TreeUtils.sortTree(roots, Comparator.comparing((Node n) -> n.id).reversed(),
                    kidsOf());
            var rootIds = roots.stream().map(n -> n.id).toList();
            Node root1 = TreeUtils.findById(roots, "1", idOf(), kidsOf());
            var kidIds = root1.children.stream().map(n -> n.id).toList();
            Node node2 = TreeUtils.findById(roots, "2", idOf(), kidsOf());
            var grandKidIds = node2.children.stream().map(n -> n.id).toList();
            var ok = rootIds.equals(List.of("1"))
                    && kidIds.equals(List.of("3", "2"))
                    && grandKidIds.equals(List.of("5", "4"));
            ExampleUtils.print("sortTreeRecursive", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("sortTreeRecursive", e);
        }
    }

    /**
     * 场景：flatten 按 DFS 前序输出全部节点且总数正确。
     *
     * @return true 表示通过
     */
    private static boolean flattenPreOrder() {
        try {
            List<Node> roots = buildSampleRoots();
            List<Node> flat = TreeUtils.flatten(roots, kidsOf());
            var ids = flat.stream().map(n -> n.id).toList();
            var ok = ids.equals(List.of("1", "2", "4", "5", "3", "6"));
            ExampleUtils.print("flattenPreOrder (" + ids + ")", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("flattenPreOrder", e);
        }
    }

    /**
     * 场景：深度计算 — 根为 0、中层为 1、叶子为 2、未找到为 -1。
     *
     * @return true 表示通过
     */
    private static boolean depthCalculation() {
        try {
            List<Node> roots = buildSampleRoots();
            Node node5 = TreeUtils.findById(roots, "5", idOf(), kidsOf());
            int dRoot = TreeUtils.getDepth(roots, roots.getFirst(), kidsOf());
            int dMid = TreeUtils.getDepth(roots, TreeUtils.findById(roots, "2", idOf(), kidsOf()),
                    kidsOf());
            int dLeaf = TreeUtils.getDepth(roots, node5, kidsOf());
            int dMiss = TreeUtils.getDepth(roots, new Node("ghost", null), kidsOf());
            var ok = dRoot == 0 && dMid == 1 && dLeaf == 2 && dMiss == -1;
            ExampleUtils.print("depthCalculation", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("depthCalculation", e);
        }
    }

    // ==================== 辅助 ====================

    /**
     * 构建标准示例树并返回根列表。
     *
     * @return 根节点列表
     */
    private static List<Node> buildSampleRoots() {
        return TreeUtils.build(sampleFlat(), idOf(), pidOf(),
                (n, kids) -> n.children = kids);
    }
}
