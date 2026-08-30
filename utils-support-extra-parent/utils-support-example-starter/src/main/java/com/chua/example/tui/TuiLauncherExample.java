package com.chua.example.tui;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.tui.support.TuiDashboard;
import com.chua.tui.support.TuiDashboardBuilder;
import com.chua.tui.support.TuiLayout;
import com.chua.tui.support.dashboard.SystemMonitorService;
import com.chua.tui.support.widgets.CpuWidget;
import com.chua.tui.support.widgets.DiskWidget;
import com.chua.tui.support.widgets.HtopWidget;
import com.chua.tui.support.widgets.MemoryWidget;

/**
 * TUI 仪表盘启动示例：构造 2x2 网格展示 CPU / 内存 / 磁盘 / Htop 四个组件。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class TuiLauncherExample {
    private TuiLauncherExample() { }

    /** 刷新间隔（毫秒） */
    private static final long REFRESH_INTERVAL_MS = 2000L;
    /** 启动等待时间（毫秒） */
    private static final long STARTUP_WAIT_MS = 3000L;

    /**
     * 入口方法，解析命令行参数并运行对应示例。
     * @param args 命令行参数，支持 --key=value 格式
     */
    public static void main(String[] args) throws Exception {
        SystemMonitorService monitorService = new SystemMonitorService();
        TuiDashboard dashboard = TuiDashboardBuilder.create()
                .layout(TuiLayout.GRID_2x2)
                .title("系统监控仪表盘")
                .refreshInterval(REFRESH_INTERVAL_MS)
                .registerHandler(monitorService)
                .addWidget(new CpuWidget("cpu", "CPU"))
                .addWidget(new MemoryWidget("memory", "内存"))
                .addWidget(new DiskWidget("disk", "磁盘"))
                .addWidget(new HtopWidget("htop", "Htop"))
                .showOnStart("cpu", "memory", "disk")
                .build();

        dashboard.start();
        ThreadUtils.sleep(STARTUP_WAIT_MS);
        dashboard.stop();
        log.info("[TuiLauncher] ok");
    }
}
