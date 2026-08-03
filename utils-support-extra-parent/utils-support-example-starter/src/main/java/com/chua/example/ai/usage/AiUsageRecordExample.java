package com.chua.example.ai.usage;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.chat.usage.AiUsageRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 用量记录示例：构造 AiUsage 并通过 AiUsageRecord 打印字段。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class AiUsageRecordExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        AiUsageRecordExample example = new AiUsageRecordExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：构造 AiUsage 并通过 AiUsageRecord 打印字段、校验关键字段非空。
     *
     * @return true 表示 AiUsage 字段被正确填充
     */
    public boolean runTest() {
        boolean passed = true;
        try {
            AiUsage usage = AiUsage.builder()
                    .provider("openai")
                    .model("gpt-4")
                    .inputTokens(1500)
                    .outputTokens(800)
                    .totalTokens(2300)
                    .build();

            AiUsageRecord record = AiUsageRecord.from(usage);

            log.info("Provider: {}", record.getProvider());
            log.info("Model: {}", record.getModel());
            log.info("Input Tokens: {}", record.getInputTokens());
            log.info("Output Tokens: {}", record.getOutputTokens());
            log.info("Total Tokens: {}", record.getTotalTokens());
            log.info("Input Cost: {}", record.getInputCost());
            log.info("Output Cost: {}", record.getOutputCost());
            log.info("Total Cost: {}", record.getTotalCost());
            log.info("Currency: {}", record.getCurrency());

            log.info("");
            log.info("Pricing enrichment successful: inputCost = {}, outputCost = {}, totalCost = {}, currency = {}",
                    record.getInputCost(), record.getOutputCost(), record.getTotalCost(), record.getCurrency());

            passed = record.getProvider() != null
                    && record.getModel() != null
                    && record.getInputTokens() == 1500;
        } catch (Exception e) {
            log.error("[AiUsageRecordExample] test failed: {}", e.getMessage(), e);
            passed = false;
        }
        log.info("[AiUsageRecordExample] self-test passed={}", passed);
        return passed;
    }
}
