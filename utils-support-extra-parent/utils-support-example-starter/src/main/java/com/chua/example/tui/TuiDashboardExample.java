package com.chua.example.tui;

import com.chua.common.support.network.ipc.annotations.IpcMethod;
import com.chua.common.support.utils.CommandLine;
import com.chua.tui.support.MordantHelper;
import com.chua.tui.support.TuiDashboard;
import com.chua.tui.support.TuiDashboardBuilder;
import com.chua.tui.support.TuiLayout;
import com.chua.tui.support.TuiWidget;
import com.chua.tui.support.dashboard.SystemMonitorService;
import com.chua.tui.support.widgets.CpuWidget;
import com.chua.tui.support.widgets.DiskWidget;
import com.chua.tui.support.widgets.HtopWidget;
import com.chua.tui.support.widgets.MemoryWidget;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

/**
 * TUI 仪表盘综合示例 — 覆盖布局、组件、构建器、Mordant 渲染、处理器绑定、colspan 区域布局。
 *
 * <p>通过命令行参数指定能力点，自检覆盖基础能力矩阵。</p>
 *
 * <h2>用法</h2>
 * <pre>
 * # 执行全部能力点自检
 * java TuiDashboardExample
 *
 * # 指定能力点测试
 * java TuiDashboardExample --type layout
 * java TuiDashboardExample --type widget
 * java TuiDashboardExample --type builder
 * java TuiDashboardExample --type capacity
 * java TuiDashboardExample --type render
 * java TuiDashboardExample --type mordant
 * java TuiDashboardExample --type handler
 * java TuiDashboardExample --type dashboard
 * java TuiDashboardExample --type colspan
 *
 * # 启动固定 2x2 仪表盘
 * java TuiDashboardExample --run
 *
 * # 启动自由网格仪表盘（top + bottom 左右分栏）
 * java TuiDashboardExample --run-free
 *
 * # 打印帮助
 * java TuiDashboardExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 * <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 * <tr><td>布局枚举</td><td>{@link #testLayout()}</td><td>验证 TuiLayout 行列与容量</td></tr>
 * <tr><td>组件基础</td><td>{@link #testWidget()}</td><td>验证 TuiWidget id/title/rawData</td></tr>
 * <tr><td>构建器</td><td>{@link #testBuilder()}</td><td>验证链式构建与 build() 返回对象</td></tr>
 * <tr><td>容量校验</td><td>{@link #testCapacity()}</td><td>验证组件数量超出容量时抛异常</td></tr>
 * <tr><td>组件渲染</td><td>{@link #testWidgetRender()}</td><td>验证各 Widget render 非空</td></tr>
 * <tr><td>Mordant 工具</td><td>{@link #testMordantHelper()}</td><td>验证进度条/颜色/面板渲染</td></tr>
 * <tr><td>处理器绑定</td><td>{@link #testHandlerBinding()}</td><td>验证 @IpcMethod 注册与数据解析</td></tr>
 * <tr><td>仪表盘启停</td><td>{@link #testDashboard()}</td><td>验证 start/stop/renderAll 生命周期</td></tr>
 * <tr><td>colspan 区域布局</td><td>{@link #testColspan()}</td><td>验证 FREE_GRID + colspan 混合布局</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class `TuiDashboardExample implements Example {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认能力点类型
     */
    private static final String DEFAULT_TYPE = "all";

    /**
     * 默认布局规格
     */
    private static final TuiLayout DEFAULT_LAYOUT = TuiLayout.GRID_2x2;

    /**
     * 仪表盘启停测试观察时长（毫秒）
     */
    private static final long DASHBOARD_RUN_MILLIS = 500L;

    // ==================== main ====================

    /**
     * 主入口：根据命令行参数运行指定能力点自检或启动真实仪表盘。
     *
     * @param args 命令行参数数组
     */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("TuiDashboardExample")
                .register("type", "t", "能力点类型（layout|widget|builder|capacity|render|mordant|handler|dashboard|colspan|all）", DEFAULT_TYPE)
                .register("run", "常驻启动真实仪表盘（固定 2x2）")
                .register("run-free", "常驻启动自由网格仪表盘（top+bottom 分栏）")
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        if (cli.has("run-free")) {
            new TuiDashboardExample().runDashboardFreeGrid();
            return;
        }

        TuiDashboardExample example = new TuiDashboardExample();
        if (cli.has("run")) {
            example.runDashboard();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);
        boolean passed = example.runTest(type);
        log.info("[TuiDashboardExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 启动自检流程：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = DEFAULT_TYPE;
        }
        switch (type.toLowerCase()) {
            case "layout":
                return testLayout();
            case "widget":
                return testWidget();
            case "builder":
                return testBuilder();
            case "capacity":
                return testCapacity();
            case "render":
                return testWidgetRender();
            case "mordant":
                return testMordantHelper();
            case "handler":
                return testHandlerBinding();
            case "dashboard":
                return testDashboard();
            case "colspan":
                return testColspan();
            case "all":
                return testLayout()
                        && testWidget()
                        && testBuilder()
                        && testCapacity()
                        && testWidgetRender()
                        && testMordantHelper()
                        && testHandlerBinding()
                        && testDashboard()
                        && testColspan();
            default:
                log.error("[TuiDashboardExample] 未知能力点: {}", type);
                return false;
        }
    }

    // ==================== 基础能力点 ====================

    /**
     * 布局枚举自检：验证 TuiLayout 各常量的行列与容量。
     *
     * @return true 表示所有布局规格符合预期
     */
    public boolean testLayout() {
        log.info("===== [layout] 布局枚举测试 =====");

        boolean allPassed = true;

        allPassed &= checkLayout(TuiLayout.GRID_1x1, 1, 1, 1);
        allPassed &= checkLayout(TuiLayout.GRID_2x2, 2, 2, 4);
        allPassed &= checkLayout(TuiLayout.GRID_3x3, 3, 3, 9);
        allPassed &= checkLayout(TuiLayout.GRID_4x4, 4, 4, 16);
        allPassed &= checkLayout(TuiLayout.FREE_GRID, 0, 0, 0);

        log.info(" [layout] passed={}", allPassed);
        return allPassed;
    }

    /**
     * 组件基础自检：验证 TuiWidget 的 id、title、rawData、colspan/rowspan 存取。
     *
     * @return true 表示组件基础行为符合预期
     */
    public boolean testWidget() {
        log.info("===== [widget] 组件基础测试 =====");

        CpuWidget cpuWidget = new CpuWidget("cpu", "CPU 使用率");
        boolean idOk = "cpu".equals(cpuWidget.getId());
        boolean titleOk = "CPU 使用率".equals(cpuWidget.getTitle());
        boolean nullDataOk = cpuWidget.getRawData() == null;

        cpuWidget.setRawData("45.2");
        boolean setDataOk = "45.2".equals(cpuWidget.getRawData());

        cpuWidget.setColspan(2);
        cpuWidget.setRowspan(1);
        boolean colspanOk = cpuWidget.getColspan() == 2;
        boolean rowspanOk = cpuWidget.getRowspan() == 1;

        boolean passed = idOk && titleOk && nullDataOk && setDataOk && colspanOk && rowspanOk;
        log.info(" [widget] passed={}", passed);
        return passed;
    }

    /**
     * 构建器自检：验证 TuiDashboardBuilder 链式调用与 build() 返回对象。
     *
     * @return true 表示构建器链式 API 符合预期
     */
    public boolean testBuilder() {
        log.info("===== [builder] 构建器测试 =====");

        try {
            TuiDashboardBuilder builder = TuiDashboardBuilder.create()
                    .layout(DEFAULT_LAYOUT)
                    .title("测试仪表盘")
                    .refreshInterval(2000L)
                    .addWidget(new CpuWidget())
                    .addWidget(new MemoryWidget());

            TuiDashboardBuilder builderWithHandler = builder.registerHandler(new Object() {
                @SuppressWarnings("unused")
                /** 获取Data */
                public String getData() {
                    return "42.0";
                }
            });

            var dashboard = builderWithHandler.build();

            boolean notNull = dashboard != null;
            boolean titleOk = "测试仪表盘".equals(dashboard.getTitle());

            log.info(" [builder] passed={}", (notNull && titleOk));
            return notNull && titleOk;
        } catch (Exception e) {
            log.error("[TuiDashboardExample] builder failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 容量校验自检：验证组件数量超出布局容量时抛出 IllegalStateException。
     *
     * @return true 表示超出容量时正确抛异常
     */
    public boolean testCapacity() {
        log.info("===== [capacity] 容量校验测试 =====");

        try {
            TuiDashboardBuilder builder = TuiDashboardBuilder.create()
                    .layout(TuiLayout.GRID_1x1)
                    .addWidget(new CpuWidget())
                    .addWidget(new MemoryWidget());

            builder.build();
            log.info(" [capacity] passed=false (应抛异常但未抛)");
            return false;
        } catch (IllegalStateException e) {
            boolean msgOk = e.getMessage() != null
                    && e.getMessage().contains("超出布局容量");
            log.info(" [capacity] passed={}", msgOk);
            return msgOk;
        } catch (Exception e) {
            log.error("[TuiDashboardExample] capacity failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 进阶能力点 ====================

    /**
     * 组件渲染自检：验证各 Widget render 方法能正常输出非空字符串。
     *
     * @return true 表示所有组件渲染结果均非空
     */
    public boolean testWidgetRender() {
        log.info("===== [render] 组件渲染测试 =====");

        CpuWidget cpuWidget = new CpuWidget("cpu", "CPU");
        MemoryWidget memoryWidget = new MemoryWidget("memory", "内存");
        DiskWidget diskWidget = new DiskWidget("disk", "磁盘");
        HtopWidget htopWidget = new HtopWidget("htop", "Htop");

        boolean cpuOk = isNonEmpty(cpuWidget.render("45.2"));
        boolean memOk = isNonEmpty(memoryWidget.render("45.2|8.2|16"));
        boolean diskOk = isNonEmpty(diskWidget.render("C:|100|200|50.0"));
        boolean htopOk = isNonEmpty(htopWidget.render("10.0|20.0|4|8\njava|1|5.0|10.0"));

        boolean passed = cpuOk && memOk && diskOk && htopOk;
        log.info(" [render] passed={}", passed);
        return passed;
    }

    /**
     * Mordant 工具自检：验证进度条、颜色、面板等基础渲染能力。
     *
     * @return true 表示 Mordant 工具方法返回非空且格式正常
     */
    public boolean testMordantHelper() {
        log.info("===== [mordant] MordantHelper 测试 =====");

        String bar = MordantHelper.progressBar(50, 10);
        String barWithPct = MordantHelper.progressBarWithPercent(75.5, 20);
        String green = MordantHelper.color("green", "green");
        String bold = MordantHelper.bold("bold");
        String dim = MordantHelper.dim("dim");
        String panel = MordantHelper.panel("hello", "title", "single");

        boolean barOk = bar != null && bar.length() > 0;
        boolean barPctOk = barWithPct != null && barWithPct.contains("75.5");
        boolean colorOk = green != null && green.length() > 0;
        boolean boldOk = bold != null && bold.length() > 0;
        boolean dimOk = dim != null && dim.length() > 0;
        boolean panelOk = panel != null && panel.length() > 0;

        boolean passed = barOk && barPctOk && colorOk && boldOk && dimOk && panelOk;
        log.info(" [mordant] passed={}", passed);
        return passed;
    }

    /**
     * 处理器绑定自检：验证 @IpcMethod 注册后构建器能成功 build 并启动。
     *
     * @return true 表示处理器注册成功且仪表盘可正常启动停止
     */
    public boolean testHandlerBinding() {
        log.info("===== [handler] 处理器绑定测试 =====");

        try {
            TestHandler handler = new TestHandler();
            TuiDashboard dashboard = TuiDashboardBuilder.create()
                    .layout(TuiLayout.GRID_1x1)
                    .title("handler-test")
                    .refreshInterval(1000L)
                    .registerHandler(handler)
                    .addWidget(new CpuWidget("test", "Test"))
                    .build();

            dashboard.start();
            Thread.sleep(DASHBOARD_RUN_MILLIS);
            dashboard.stop();

            boolean ok = "handler-test".equals(dashboard.getTitle());
            log.info(" [handler] passed={}", ok);
            return ok;
        } catch (Exception e) {
            log.error("[TuiDashboardExample] handler failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 仪表盘启停自检：验证 start/stop 生命周期与 renderAll 不抛异常。
     *
     * @return true 表示启动后能正常渲染并停止
     */
    public boolean testDashboard() {
        log.info("===== [dashboard] 仪表盘启停测试 =====");

        try {
            TestHandler handler = new TestHandler();
            TuiDashboard dashboard = TuiDashboardBuilder.create()
                    .layout(TuiLayout.GRID_1x1)
                    .title("启停测试")
                    .refreshInterval(1000L)
                    .registerHandler(handler)
                    .addWidget(new CpuWidget("test", "Test"))
                    .build();

            dashboard.start();
            boolean runningBefore = dashboard.isRunning();
            Thread.sleep(DASHBOARD_RUN_MILLIS);
            dashboard.stop();
            boolean runningAfter = dashboard.isRunning();

            boolean passed = runningBefore && !runningAfter;
            log.info(" [dashboard] runningBefore={} runningAfter={} passed={}", runningBefore, runningAfter, passed);
            return passed;
        } catch (Exception e) {
            log.error("[TuiDashboardExample] dashboard failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * colspan 区域布局自检：验证 FREE_GRID 下组件按 colspan 自动换行。
     *
     * @return true 表示 FREE_GRID 构建与渲染不抛异常
     */
    public boolean testColspan() {
        log.info("===== [colspan] 区域布局测试 =====");

        try {
            CpuWidget topWidget = new CpuWidget("top", "Top Bar");
            topWidget.setColspan(2);

            MemoryWidget leftWidget = new MemoryWidget("left", "Left");
            DiskWidget rightWidget = new DiskWidget("right", "Right");

            TuiDashboard dashboard = TuiDashboardBuilder.create()
                    .layout(TuiLayout.FREE_GRID)
                    .title("colspan 测试")
                    .addWidget(topWidget)
                    .addWidget(leftWidget)
                    .addWidget(rightWidget)
                    .build();

            dashboard.start();
            Thread.sleep(DASHBOARD_RUN_MILLIS);
            dashboard.stop();

            boolean passed = dashboard.getVisibleWidgets().size() == 3;
            log.info(" [colspan] passed={}", passed);
            return passed;
        } catch (Exception e) {
            log.error("[TuiDashboardExample] colspan failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断字符串是否非空。
     *
     * @param value 待检查字符串
     * @return true 表示非空
     */
    private static boolean isNonEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * 检查单个布局规格。
     *
     * @param layout           布局枚举
     * @param expectedRows     预期行数
     * @param expectedCols     预期列数
     * @param expectedCapacity 预期容量
     * @return true 表示所有属性符合预期
     */
    private static boolean checkLayout(TuiLayout layout, int expectedRows, int expectedCols, int expectedCapacity) {
        boolean rowsOk = layout.getRows() == expectedRows;
        boolean colsOk = layout.getCols() == expectedCols;
        boolean capOk = layout.getCapacity() == expectedCapacity;
        boolean passed = rowsOk && colsOk && capOk;
        log.info("  {} rows={}/{} cols={}/{} cap={}/{} -> {}", layout,
                layout.getRows(), expectedRows,
                layout.getCols(), expectedCols,
                layout.getCapacity(), expectedCapacity,
                (passed ? "PASS" : "FAIL"));
        return passed;
    }

    // ==================== 常驻仪表盘模式 ====================

    /**
     * 常驻启动真实仪表盘：使用 SystemMonitorService 提供实时数据。
     * <p>
     * 启动后支持运行时显示/隐藏/动态增删组件：
     * <pre>
     *   q              退出
     *   r              手动刷新
     *   h &lt;id&gt;         隐藏组件
     *   s &lt;id&gt;         显示组件
     *   t &lt;id&gt;         切换组件显示状态
     *   a &lt;id&gt; &lt;title&gt;  添加新组件
     *   d &lt;id&gt;         删除组件
     *   l              列出当前组件
     * </pre>
     * </p>
     */
    public void runDashboard() {
        log.info("[TuiDashboardExample] 启动真实仪表盘...");
        log.info("[TuiDashboardExample] 支持命令: q=退出 r=刷新 h=隐藏 s=显示 t=切换 a=添加 d=删除 l=列表");
        log.info("[TuiDashboardExample] 示例: h htop | s disk | t memory | a net 网络 | d htop | l");

        try {
            SystemMonitorService monitorService = new SystemMonitorService();
            TuiDashboard dashboard = TuiDashboardBuilder.create()
                    .layout(DEFAULT_LAYOUT)
                    .title("系统监控仪表盘")
                    .refreshInterval(2000L)
                    .registerHandler(monitorService)
                    .addWidget(new CpuWidget("cpu", "CPU"))
                    .addWidget(new MemoryWidget("memory", "内存"))
                    .addWidget(new DiskWidget("disk", "磁盘"))
                    .addWidget(new HtopWidget("htop", "Htop"))
                    // 创建时默认隐藏 Htop，展示 showOnStart
                    .showOnStart("cpu", "memory", "disk")
                    .build();

            dashboard.start();

            // 启动命令解析线程
            startCommandLoop(dashboard);

            dashboard.waitForExit();
        } catch (Exception e) {
            log.error("[TuiDashboardExample] runDashboard failed: {}", e.getMessage());
        }
    }

    /**
     * 常驻启动自由网格仪表盘：演示 top + bottom 左右分栏布局。
     * <p>
     * top 组件 colspan=2 占满整行，bottom 两个组件各占 1 列。
     * </p>
     */
    public void runDashboardFreeGrid() {
        log.info("[TuiDashboardExample] 启动自由网格仪表盘（top + bottom 分栏）...");
        log.info("[TuiDashboardExample] 按 q 退出");

        try {
            SystemMonitorService monitorService = new SystemMonitorService();
            CpuWidget topWidget = new CpuWidget("top", "Top Bar");
            topWidget.setColspan(2);

            MemoryWidget leftWidget = new MemoryWidget("left", "Left");
            DiskWidget rightWidget = new DiskWidget("right", "Right");

            TuiDashboard dashboard = TuiDashboardBuilder.create()
                    .layout(TuiLayout.FREE_GRID)
                    .title("系统监控（自由网格）")
                    .refreshInterval(2000L)
                    .registerHandler(monitorService)
                    .addWidget(topWidget)
                    .addWidget(leftWidget)
                    .addWidget(rightWidget)
                    .build();

            dashboard.start();
            dashboard.waitForExit();
        } catch (Exception e) {
            log.error("[TuiDashboardExample] runDashboardFreeGrid failed: {}", e.getMessage());
        }
    }

    /**
     * 启动命令行交互循环。
     *
     * @param dashboard 仪表盘实例
     */
    private static void startCommandLoop(TuiDashboard dashboard) {
        Thread commandThread = new Thread(() -> {
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in));
                String line;
                while (dashboard.isRunning() && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\\s+", 3);
                    String cmd = parts[0].toLowerCase();
                    switch (cmd) {
                        case "q":
                            dashboard.stop();
                            break;
                        case "r":
                            log.info("[cmd] 手动刷新");
                            // 触发一次渲染：依赖定时器自动渲染，这里仅打印提示
                            break;
                        case "h":
                            if (parts.length >= 2) {
                                dashboard.hideWidget(parts[1]);
                                log.info("[cmd] 隐藏: {}", parts[1]);
                            }
                            break;
                        case "s":
                            if (parts.length >= 2) {
                                dashboard.showWidget(parts[1]);
                                log.info("[cmd] 显示: {}", parts[1]);
                            }
                            break;
                        case "t":
                            if (parts.length >= 2) {
                                dashboard.toggleWidget(parts[1]);
                                log.info("[cmd] 切换: {}", parts[1]);
                            }
                            break;
                        case "d":
                            if (parts.length >= 2) {
                                dashboard.removeWidget(parts[1]);
                                log.info("[cmd] 删除: {}", parts[1]);
                            }
                            break;
                        case "a":
                            String id = parts.length >= 2 ? parts[1] : "widget-" + System.currentTimeMillis();
                            String title = parts.length >= 3 ? parts[2] : id;
                            CpuWidget newWidget = new CpuWidget(id, title);
                            dashboard.addWidget(newWidget);
                            log.info("[cmd] 添加: {} / {}", id, title);
                            break;
                        case "l":
                            log.info("[cmd] 当前组件列表:");
                            for (TuiWidget w : dashboard.getAllWidgets()) {
                                log.info("  {} | {} | visible={}", w.getId(), w.getTitle(), w.isVisible());
                            }
                            break;
                        default:
                            log.info("[cmd] 未知命令: {}，支持 q/r/h/s/t/d/a/l", cmd);
                            break;
                    }
                }
            } catch (Exception e) {
                // 输入流关闭，正常退出
            }
        }, "tui-dashboard-command");
        commandThread.setDaemon(true);
        commandThread.start();
    }

    // ==================== 测试用内部处理器 ====================

    /**
     * 测试用处理器，提供固定返回值。
     */
    public static class TestHandler {

        /**
         * 测试数据提供方法。
         *
         * @return 固定值 42
         */
        @IpcMethod("/test")
        public String getTestData() {
            return "42";
        }
    }
}
