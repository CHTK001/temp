package com.chua.example.tui;

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
public class TuiLauncher {
    /** Main */
    public static void main(String[] args) throws Exception {
        SystemMonitorService monitorService = new SystemMonitorService();
        TuiDashboard dashboard = TuiDashboardBuilder.create()
                .layout(TuiLayout.GRID_2x2)
                .title("系统监控仪表盘")
                .refreshInterval(2000L)
                .registerHandler(monitorService)
                .addWidget(new CpuWidget("cpu", "CPU"))
                .addWidget(new MemoryWidget("memory", "内存"))
                .addWidget(new DiskWidget("disk", "磁盘"))
                .addWidget(new HtopWidget("htop", "Htop"))
                .showOnStart("cpu", "memory", "disk")
                .build();

        dashboard.start();
        Thread.sleep(3000);
        dashboard.stop();
        System.out.println("[TuiLauncher] ok");
    }
}
