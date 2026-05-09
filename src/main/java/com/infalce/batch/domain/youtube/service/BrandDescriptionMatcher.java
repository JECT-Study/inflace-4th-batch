package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.repository.BrandAliasRepository;
import com.infalce.batch.entity.brand.Brand;
import com.infalce.batch.entity.brand.BrandAlias;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
@RequiredArgsConstructor
public class BrandDescriptionMatcher {

    private volatile BrandAliasIndex cachedIndex;

    private final BrandAliasRepository brandAliasRepository;

    public List<BrandMatch> matchDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return List.of();
        }
        return loadIndex().match(description);
    }

    public boolean hasAliasesConfigured() {
        return !loadIndex().isEmpty();
    }

    private BrandAliasIndex loadIndex() {
        BrandAliasIndex local = cachedIndex;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (cachedIndex == null) {
                cachedIndex = BrandAliasIndex.of(brandAliasRepository.findAllWithBrand());
            }
            return cachedIndex;
        }
    }

    public record BrandMatch(Brand brand, String matchedAlias) {
    }

    private static final class BrandAliasIndex {
        private final TrieNode root;
        private final boolean empty;

        private BrandAliasIndex(TrieNode root, boolean empty) {
            this.root = root;
            this.empty = empty;
        }

        boolean isEmpty() {
            return empty;
        }

        static BrandAliasIndex of(List<BrandAlias> aliases) {
            if (aliases == null || aliases.isEmpty()) {
                return new BrandAliasIndex(new TrieNode(), true);
            }

            TrieNode root = new TrieNode();
            boolean added = false;
            for (BrandAlias alias : aliases) {
                if (alias == null || alias.getBrand() == null || !StringUtils.hasText(alias.getAlias())) {
                    continue;
                }

                String normalizedAlias = normalize(alias.getAlias());
                if (!StringUtils.hasText(normalizedAlias)) {
                    continue;
                }

                TrieNode node = root;
                for (char ch : normalizedAlias.toCharArray()) {
                    node = node.children.computeIfAbsent(ch, key -> new TrieNode());
                }
                node.matches.add(new AliasMatch(alias.getBrand(), alias.getAlias(), normalizedAlias));
                added = true;
            }

            return new BrandAliasIndex(root, !added);
        }

        List<BrandMatch> match(String description) {
            if (empty) {
                return List.of();
            }

            String normalizedDescription = normalize(description);
            if (!StringUtils.hasText(normalizedDescription)) {
                return List.of();
            }

            Map<Long, AliasMatch> matchedByBrandId = new LinkedHashMap<>();
            char[] chars = normalizedDescription.toCharArray();
            for (int start = 0; start < chars.length; start++) {
                TrieNode node = root.children.get(chars[start]);
                if (node == null) {
                    continue;
                }

                collectMatches(node, matchedByBrandId);
                for (int next = start + 1; next < chars.length; next++) {
                    node = node.children.get(chars[next]);
                    if (node == null) {
                        break;
                    }
                    collectMatches(node, matchedByBrandId);
                }
            }

            return matchedByBrandId.values().stream()
                    .map(match -> new BrandMatch(match.brand(), match.alias()))
                    .toList();
        }

        private void collectMatches(TrieNode node, Map<Long, AliasMatch> matchedByBrandId) {
            for (AliasMatch match : node.matches) {
                AliasMatch existing = matchedByBrandId.get(match.brand().getId());
                if (existing == null || match.normalizedAlias().length() > existing.normalizedAlias().length()) {
                    matchedByBrandId.put(match.brand().getId(), match);
                }
            }
        }

        private static String normalize(String value) {
            String lowerCased = value.toLowerCase(Locale.ROOT);
            String sanitized = lowerCased.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHangul}]+", " ");
            return sanitized.replaceAll("\\s+", " ").trim();
        }
    }

    private record AliasMatch(Brand brand, String alias, String normalizedAlias) {
    }

    private static final class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private final List<AliasMatch> matches = new ArrayList<>();
    }
}
