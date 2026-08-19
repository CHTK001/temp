package com.chua.example.ai.usage;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.chat.pricing.ModelPricingProvider;
import com.chua.common.support.spi.annotations.Extension;

import java.math.BigDecimal;

/**
 * 示例用的模型定价提供器，仅内置 {@code openai/gpt-4} 一种定价，其他模型返回 null。
 *
 * @author CH
 * @since 4.0.0
 */
@Extension("openai")
public class ExampleModelPricingProvider implements ModelPricingProvider {

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
}
