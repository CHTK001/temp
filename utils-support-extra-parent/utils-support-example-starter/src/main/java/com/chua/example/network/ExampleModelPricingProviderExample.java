package com.chua.example.ai.usage;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.chat.pricing.ModelPricingProvider;
import com.chua.common.support.spi.annotations.Extension;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;

/**
 * 示例用的模型定价提供器，仅内置 {@code openai/gpt-4} 一种定价，其他模型返回 null。
 *
 * @author CH
 * @since 4.0.0
  *
 * <p>SPI 实现载体：SPI 服务实现载体，由 ExampleRunner 按类型加载，无独立 main 入口。</p>
 */
@Extension("openai")
@Slf4j
public class ExampleModelPricingProviderExample implements ModelPricingProvider {

    /** 私有构造，防止实例化 */
    private ExampleModelPricingProviderExample() { }

    @Override
    /** 获取ModelPricing */
    public ModelDefinition getModelPricing(String provider, String model) {
        if ("openai".equalsIgnoreCase(provider) && "gpt-4".equalsIgnoreCase(model)) {
            return ModelDefinition.builder()
                    .id(model)
                    .name(model)
                    .provider(provider)
                    .inputUnitPrice(new BigDecimal("0.03"))
                    .outputUnitPrice(new BigDecimal("0.06"))
                    .currency("USD")
                    .build();
        }
        return null;
    }
    /**
     * 自检入口：验证定价查询逻辑。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        ExampleModelPricingProviderExample provider = new ExampleModelPricingProviderExample();
        ModelDefinition hit = provider.getModelPricing("openai", "gpt-4");
        boolean ok = hit != null && provider.getModelPricing("other", "x") == null;
        log.info("pricing hit=" + (hit != null) + " -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}
