package com.chua.example.runner;

import com.chua.spider.support.config.model.SpiderDefinition;
import com.chua.spider.support.config.store.SpiderDefinitionStore;

/**
 * 爬虫定义存储快速验证。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SpiderStoreTest {

    public static void main(String[] args) {
        SpiderDefinitionStore store = new SpiderDefinitionStore();

        // 新增
        SpiderDefinition def = new SpiderDefinition();
        def.setSpiderCode("test-spider");
        def.setSpiderName("测试爬虫");
        def.setSpiderEntryUrl("https://example.com");
        def.setSpiderMaxDepth(2);
        def.setSpiderStatus(1);
        store.save(def);
        System.out.println("[PASS] 新增: " + def.getSpiderId());

        // 查询
        SpiderDefinition found = store.get("test-spider");
        System.out.println("[PASS] 查询: " + (found != null ? found.getSpiderName() : "null"));

        // 分页
        var page = store.page(1, 10, null);
        System.out.println("[PASS] 分页: total=" + page.total() + ", records=" + page.records().size());

        // 启停
        store.updateStatus("test-spider", 0);
        SpiderDefinition disabled = store.get("test-spider");
        System.out.println("[PASS] 启停: status=" + disabled.getSpiderStatus());

        // 删除
        boolean removed = store.remove("test-spider");
        System.out.println("[PASS] 删除: " + removed);

        // 验证空列表
        var emptyPage = store.page(1, 10, null);
        System.out.println("[PASS] 删除后空: " + (emptyPage.total() == 0));

        System.out.println("全部通过");
    }
}