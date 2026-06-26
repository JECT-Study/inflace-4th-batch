package com.infalce.batch.domain.youtube.ai;

public enum OpenAiModel {
    GPT_4O("gpt-4o"),
    GPT_4O_MINI("gpt-4o-mini"),

    GPT_4_1("gpt-4.1"),
    GPT_4_1_MINI("gpt-4.1-mini"),

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small"),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large"),

    GPT_3_5_TURBO("gpt-3.5-turbo");

    private final String modelName;

    OpenAiModel(String modelName) {
        this.modelName = modelName;
    }

    public String modelName() {
        return modelName;
    }
}
