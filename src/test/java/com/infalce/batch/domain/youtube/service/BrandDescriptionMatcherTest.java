package com.infalce.batch.domain.youtube.service;

import com.infalce.batch.domain.youtube.repository.BrandAliasRepository;
import com.infalce.batch.entity.brand.Brand;
import com.infalce.batch.entity.brand.BrandAlias;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandDescriptionMatcherTest {

    @Test
    void matchesBrandsFromDescriptionUsingLongestAliasPerBrand() {
        Brand samsung = brand(1L, "삼성전자");
        Brand nike = brand(2L, "나이키");

        BrandAliasRepository repository = mock(BrandAliasRepository.class);
        when(repository.findAllWithBrand()).thenReturn(List.of(
                brandAlias(11L, samsung, "삼성"),
                brandAlias(12L, samsung, "삼성전자"),
                brandAlias(13L, nike, "나이키")
        ));

        BrandDescriptionMatcher matcher = new BrandDescriptionMatcher(repository);

        List<BrandDescriptionMatcher.BrandMatch> matches = matcher.matchDescription(
                "이 영상은 삼성전자와 나이키 협찬이 포함된 영상입니다."
        );

        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(match ->
                match.brand().getId().equals(1L) && match.matchedAlias().equals("삼성전자")));
        assertTrue(matches.stream().anyMatch(match ->
                match.brand().getId().equals(2L) && match.matchedAlias().equals("나이키")));
    }

    private Brand brand(Long id, String name) {
        Brand brand = instantiate(Brand.class);
        ReflectionTestUtils.setField(brand, "id", id);
        ReflectionTestUtils.setField(brand, "name", name);
        return brand;
    }

    private BrandAlias brandAlias(Long id, Brand brand, String alias) {
        BrandAlias brandAlias = instantiate(BrandAlias.class);
        ReflectionTestUtils.setField(brandAlias, "id", id);
        ReflectionTestUtils.setField(brandAlias, "brand", brand);
        ReflectionTestUtils.setField(brandAlias, "alias", alias);
        return brandAlias;
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to instantiate " + type.getSimpleName(), exception);
        }
    }
}
