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
                if (!StringUtils.hasText(normalizedAlias) || shouldSkipAlias(normalizedAlias)) {
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

            Map<Long, MatchCandidate> matchedByBrandId = new LinkedHashMap<>();
            List<MatchCandidate> acceptedMatches = new ArrayList<>();
            char[] chars = normalizedDescription.toCharArray();
            for (int start = 0; start < chars.length; start++) {
                TrieNode node = root.children.get(chars[start]);
                if (node == null) {
                    continue;
                }

                collectMatches(node, matchedByBrandId, acceptedMatches, normalizedDescription, start, start + 1);
                for (int next = start + 1; next < chars.length; next++) {
                    node = node.children.get(chars[next]);
                    if (node == null) {
                        break;
                    }
                    collectMatches(node, matchedByBrandId, acceptedMatches, normalizedDescription, start, next + 1);
                }
            }

            return matchedByBrandId.values().stream()
                    .map(match -> new BrandMatch(match.match().brand(), match.match().alias()))
                    .toList();
        }

        private void collectMatches(
                TrieNode node,
                Map<Long, MatchCandidate> matchedByBrandId,
                List<MatchCandidate> acceptedMatches,
                String description,
                int startInclusive,
                int endExclusive
        ) {
            for (AliasMatch match : node.matches) {
                if (!hasValidBoundary(description, startInclusive, endExclusive, match.normalizedAlias())) {
                    continue;
                }

                MatchCandidate candidate = new MatchCandidate(match, startInclusive, endExclusive);
                if (isOverlappedByLongerMatch(candidate, acceptedMatches)) {
                    continue;
                }

                removeShorterOverlappedMatches(candidate, matchedByBrandId, acceptedMatches);

                MatchCandidate existing = matchedByBrandId.get(match.brand().getId());
                if (existing == null || isPreferred(candidate, existing)) {
                    matchedByBrandId.put(match.brand().getId(), candidate);
                    acceptedMatches.removeIf(saved -> saved.match().brand().getId().equals(match.brand().getId()));
                    acceptedMatches.add(candidate);
                }
            }
        }

        private boolean isPreferred(MatchCandidate candidate, MatchCandidate existing) {
            int candidateLength = candidate.match().normalizedAlias().length();
            int existingLength = existing.match().normalizedAlias().length();
            if (candidateLength != existingLength) {
                return candidateLength > existingLength;
            }
            return candidate.startInclusive() < existing.startInclusive();
        }

        private boolean isOverlappedByLongerMatch(MatchCandidate candidate, List<MatchCandidate> acceptedMatches) {
            for (MatchCandidate accepted : acceptedMatches) {
                if (!isOverlap(candidate, accepted)) {
                    continue;
                }

                int candidateLength = candidate.match().normalizedAlias().length();
                int acceptedLength = accepted.match().normalizedAlias().length();
                if (acceptedLength > candidateLength) {
                    return true;
                }
                if (acceptedLength == candidateLength && accepted.startInclusive() <= candidate.startInclusive()) {
                    return true;
                }
            }
            return false;
        }

        private void removeShorterOverlappedMatches(
                MatchCandidate candidate,
                Map<Long, MatchCandidate> matchedByBrandId,
                List<MatchCandidate> acceptedMatches
        ) {
            List<MatchCandidate> toRemove = acceptedMatches.stream()
                    .filter(accepted -> isOverlap(candidate, accepted))
                    .filter(accepted -> candidate.match().normalizedAlias().length() > accepted.match().normalizedAlias().length())
                    .toList();

            for (MatchCandidate removed : toRemove) {
                matchedByBrandId.remove(removed.match().brand().getId());
                acceptedMatches.remove(removed);
            }
        }

        private boolean isOverlap(MatchCandidate left, MatchCandidate right) {
            return left.startInclusive() < right.endExclusive()
                    && right.startInclusive() < left.endExclusive();
        }

        private static boolean hasValidBoundary(String description, int startInclusive, int endExclusive, String normalizedAlias) {
            if (isAsciiWord(normalizedAlias)) {
                return isAsciiBoundary(description, startInclusive - 1)
                        && isAsciiBoundary(description, endExclusive);
            }

            if (normalizedAlias.length() <= 1) {
                return false;
            }

            return true;
        }

        private static boolean isAsciiBoundary(String description, int index) {
            if (index < 0 || index >= description.length()) {
                return true;
            }

            char ch = description.charAt(index);
            return !Character.isLetterOrDigit(ch);
        }

        private static boolean isAsciiWord(String value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch == ' ') {
                    continue;
                }
                if (ch > 127 || !Character.isLetterOrDigit(ch)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean shouldSkipAlias(String normalizedAlias) {
            if (normalizedAlias.length() <= 1) {
                return true;
            }

            if (isAsciiWord(normalizedAlias)) {
                int compactLength = normalizedAlias.replace(" ", "").length();
                return compactLength <= 2;
            }

            return false;
        }

        private static String normalize(String value) {
            String lowerCased = value.toLowerCase(Locale.ROOT);
            String sanitized = lowerCased.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHangul}]+", " ");
            return sanitized.replaceAll("\\s+", " ").trim();
        }
    }

    private record AliasMatch(Brand brand, String alias, String normalizedAlias) {
    }

    private record MatchCandidate(AliasMatch match, int startInclusive, int endExclusive) {
    }

    private static final class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private final List<AliasMatch> matches = new ArrayList<>();
    }
}
