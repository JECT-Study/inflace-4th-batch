package com.infalce.batch.domain.youtube.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "youtube.batch.brand-ai")
public class BrandAiProperties {

    private OpenAiModel model = OpenAiModel.GPT_4O_MINI;
    private Integer maxTokens = 100;
    private Double temperature = 0.1;
    private int contextRadius = 400;
    private int maxAnswerLength = 50;
}
