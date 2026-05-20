package com.infalce.batch.entity.email;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailSendType {
    EXAMPLE("예시 이메일", "emails/example-email");

    private final String title;
    private final String templatePath;
}
