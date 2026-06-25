package com.infalce.batch.domain.youtube.ai;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrandExtractionPrompt {

    private static final Pattern AD_KEYWORDS = Pattern.compile(
            "제공|협찬|후원|광고|스폰서|파트너십|제작지원|제작 지원"
                    + "|할인|이벤트|프로모션|경품|쿠폰|특가|세일"
                    + "|Sponsored|sponsored|Paid\\s*partnership|paid\\s*partnership"
                    + "|#ad\\b|#광고|#협찬|#후원|#sponsored"
                    + "|\\bad\\b|AD\\b"
                    + "|discount|giveaway|promo|coupon|\\bsale\\b",
            Pattern.CASE_INSENSITIVE
    );

    private BrandExtractionPrompt() {
    }

    public static String systemMessage() {
        return "당신은 유튜브 영상에서 유료 광고(PPL/협찬) 브랜드를 정확히 식별하는 전문가입니다. 브랜드명 하나 또는 null만 응답합니다.";
    }

    public static String humanMessage(BrandAiExtractor.BrandAiVideo video, int contextRadius) {
        String tags = video.tags() == null || video.tags().isEmpty()
                ? "없음"
                : String.join(", ", video.tags().stream()
                .filter(StringUtils::hasText)
                .limit(20)
                .toList());

        return """
                아래 유튜브 영상은 유료 광고(PPL)가 포함되어 있습니다.
                제목, 설명, 태그를 분석하여 광고/협찬 브랜드명을 추출하세요.

                제목: %s
                설명: %s
                태그: %s

                규칙:
                - 가장 주요한 광고주 브랜드명을 하나만 추출하세요.
                - 다음 키워드 주변에서 브랜드명을 찾으세요:
                  '제공', '협찬', '후원', '광고', '스폰서', '파트너십', '제작지원',
                  'Sponsored by', 'Paid partnership', '#ad', '#광고', '#협찬', '#후원'
                - 다음 패턴도 확인하세요:
                  'OOO와 함께', 'OOO에서 제공', 'OOO 광고 포함',
                  제목의 [광고], (AD), [AD] 접두사/접미사,
                  URL에 포함된 브랜드 도메인 (예: smartstore.naver.com/brandname)
                - 할인/프로모션 패턴: 'OOO N%% 할인', 'OOO 특가', 구매/할인 링크와 함께 언급된 제품 브랜드
                - 이벤트/경품 패턴: '경품: OOO', 'OOO 증정', 이벤트 상품으로 언급된 제품의 브랜드
                - 반복 언급 패턴: 설명에서 반복적으로 등장하며 기능/스펙이 상세히 나열된 제품의 브랜드
                - 해시태그 패턴: #브랜드명, #브랜드명제품명 형태의 브랜드 해시태그
                - 브랜드를 찾으면 브랜드명만 출력하세요. 찾을 수 없으면 null만 출력하세요.
                - 설명이나 부연 없이 브랜드명 또는 null만 출력하세요.

                브랜드명:
                """.formatted(
                nullToEmpty(video.title()),
                extractAdContext(video.description(), contextRadius),
                tags
        );
    }

    private static String extractAdContext(String description, int contextRadius) {
        if (!StringUtils.hasText(description)) {
            return "";
        }

        List<int[]> keywordRanges = new ArrayList<>();
        Matcher matcher = AD_KEYWORDS.matcher(description);
        while (matcher.find()) {
            keywordRanges.add(new int[]{matcher.start(), matcher.end()});
        }
        if (keywordRanges.isEmpty()) {
            return description;
        }

        List<int[]> ranges = new ArrayList<>();
        for (int[] keywordRange : keywordRanges) {
            int start = Math.max(0, keywordRange[0] - contextRadius);
            int end = Math.min(description.length(), keywordRange[1] + contextRadius);
            boolean merged = false;
            for (int[] range : ranges) {
                if (start <= range[1] && end >= range[0]) {
                    range[0] = Math.min(range[0], start);
                    range[1] = Math.max(range[1], end);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                ranges.add(new int[]{start, end});
            }
        }

        ranges.sort(java.util.Comparator.comparingInt(range -> range[0]));
        List<String> segments = new ArrayList<>();
        for (int[] range : ranges) {
            String prefix = range[0] > 0 ? "..." : "";
            String suffix = range[1] < description.length() ? "..." : "";
            segments.add(prefix + description.substring(range[0], range[1]).trim() + suffix);
        }
        return String.join("\n---\n", segments);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
