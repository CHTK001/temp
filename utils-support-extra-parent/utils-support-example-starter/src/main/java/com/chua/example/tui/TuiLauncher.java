package com.chua.example.tui;

import com.chua.tui.support.TuiDashboard;
import com.chua.tui.support.TuiDashboardBuilder;
import com.chua.tui.support.TuiLayout;
import com.chua.tui.support.dashboard.SystemMonitorService;
import com.chua.tui.support.widgets.CpuWidget;
import com.chua.tui.support.widgets.DiskWidget;
import com.chua.tui.support.widgets.HtopWidget;
import com.chua.tui.support.widgets.MemoryWidget;

public class TuiLauncher {
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
        System.out.println("[TuiLauncher] smoke test passed");
    }
}
