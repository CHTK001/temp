package com.chua.example.ai.usage;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.chat.pricing.ModelPricingProvider;
import com.chua.common.support.spi.annotations.Extension;

import java.math.BigDecimal;

@Extension("openai")
public class ExampleModelPricingProvider implements ModelPricingProvider {

    @Override
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
