package com.infalce.batch.entity.user;

public enum UserRole {
    YOUTUBER,        // 유튜버
    MARKETER,        // 마케터
    BRAND_MANAGER,   // 브랜드 담당자
    MCN_AGENCY,      // MCN / 에이전시
    CONTENT_PLANNER, // 콘텐츠 기획자
    ETC;             // 기타

    public String toSpringRole() {
        return "ROLE_" + name();
    }
}
