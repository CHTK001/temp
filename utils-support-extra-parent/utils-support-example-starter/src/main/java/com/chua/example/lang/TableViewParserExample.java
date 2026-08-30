package com.chua.example.lang;

import com.chua.common.support.lang.view.TableViewParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * TableViewParser 示例：验证默认框线模式与无边框模式的渲染行为。
 *
 * <p>覆盖场景：Map 两列（框线/无边框）、List&lt;Map&gt; 动态列、简单类型单列、
 * 空数据占位、无边框输出不含框线字符且行尾无多余空白。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.lang.TableViewParserExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TableViewParserExample {
    private TableViewParserExample() { }


    /**
     * 主入口，逐场景自检，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        Map<Object, Object> keyValue = new LinkedHashMap<>();
        keyValue.put("foo", 123);
        keyValue.put("bar", 456);

        List<Map<String, Object>> rows = List.of(
                Map.of("Name", "alice", "Age", 30),
                Map.of("Name", "bob", "Age", 25));

        boolean allPassed = true;

        // 场景 1：默认框线模式回归——必须保留全部框线字符
        String bordered = new TableViewParser().render(keyValue);
        allPassed &= verify("框线模式含竖线分隔符", bordered.contains("│"));
        allPassed &= verify("框线模式含顶线与底线", bordered.contains("┌") && bordered.contains("└"));

        // 场景 2：Map 无边框——仅空格对齐，无任何框线字符
        String plain = new TableViewParser().setBorderless(true).render(keyValue);
        allPassed &= verify("无边框模式不含任何框线字符", !containsBorderChar(plain));
        allPassed &= verify("无边框表头对齐为 Key  Value", plain.lines().findFirst().orElse("").equals("Key  Value"));
        allPassed &= verify("无边框数据行对齐为 foo  123", plain.contains("\nfoo  123"));

        // 场景 3：List<Map> 动态列无边框——多行行尾均无多余空白
        String table = new TableViewParser().setBorderless(true).render(rows);
        boolean noTrailingSpace = table.lines().allMatch(line -> line.equals(line.stripTrailing()));
        allPassed &= verify("List<Map> 无边框渲染所有行尾无空白", noTrailingSpace && !containsBorderChar(table));

        // 场景 4：简单类型单列无边框
        String simple = new TableViewParser().setBorderless(true).render(List.of("a", "b"));
        allPassed &= verify("简单类型单列无边框逐行输出", simple.equals("#\na\nb"));

        // 场景 5：空数据占位不受模式影响
        String empty = new TableViewParser().setBorderless(true).render(List.of());
        allPassed &= verify("空数据返回占位文本", "(empty)".equals(empty));

        if (allPassed) {
            log.info("[PASS] TableViewParser 全部场景通过");
        }
        System.exit(allPassed ? 0 : 1);
    }

    /**
     * 判断渲染结果是否含有任意框线字符。
     *
     * @param text 渲染结果
     * @return 含有框线字符返回 true
     */
    private static boolean containsBorderChar(String text) {
        return text.chars().anyMatch(c -> "┌┬┐├┼┤│─".indexOf(c) >= 0);
    }

    /**
     * 执行单条校验。
     *
     * @param name      校验项名称
     * @param condition 断言条件
     * @return 校验通过返回 true
     */
    private static boolean verify(String name, boolean condition) {
        if (!condition) {
            log.info("[FAIL] " + name);
            return false;
        }
        log.info("[PASS] " + name);
        return true;
    }
}
