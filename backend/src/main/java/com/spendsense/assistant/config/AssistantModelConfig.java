package com.spendsense.assistant.config;

import com.spendsense.assistant.model.ModelGateway;
import com.spendsense.assistant.model.provider.GeminiModelGateway;
import com.spendsense.assistant.model.provider.MockModelGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AssistantModelConfig {

    @Bean
    @Primary
    public ModelGateway primaryModelGateway(
            AssistantProperties properties,
            GeminiModelGateway geminiModelGateway,
            MockModelGateway mockModelGateway
    ) {
        if ("gemini".equalsIgnoreCase(properties.getProvider())) {
            return geminiModelGateway;
        }
        return mockModelGateway;
    }
}
