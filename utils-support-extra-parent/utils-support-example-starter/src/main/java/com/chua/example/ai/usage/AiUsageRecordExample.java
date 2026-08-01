package com.chua.example.ai.usage;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.chat.usage.AiUsageRecord;

/**
 * AI 用量记录示例：构造 AiUsage 并通过 AiUsageRecord 打印字段。
 *
 * @author CH
 * @since 4.0.0
 */
public class AiUsageRecordExample {

    public static void main(String[] args) {
        AiUsage usage = AiUsage.builder()
                .provider("openai")
                .model("gpt-4")
                .inputTokens(1500)
                .outputTokens(800)
                .totalTokens(2300)
                .build();

        AiUsageRecord record = AiUsageRecord.from(usage);

        System.out.println("Provider: " + record.getProvider());
        System.out.println("Model: " + record.getModel());
        System.out.println("Input Tokens: " + record.getInputTokens());
        System.out.println("Output Tokens: " + record.getOutputTokens());
        System.out.println("Total Tokens: " + record.getTotalTokens());
        System.out.println("Input Cost: " + record.getInputCost());
        System.out.println("Output Cost: " + record.getOutputCost());
        System.out.println("Total Cost: " + record.getTotalCost());
        System.out.println("Currency: " + record.getCurrency());

        System.out.println();
        System.out.println("Pricing enrichment successful: inputCost = " + record.getInputCost()
                + ", outputCost = " + record.getOutputCost()
                + ", totalCost = " + record.getTotalCost()
                + ", currency = " + record.getCurrency());
    }
}
