package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByName(String name);

    @Query("""
            select b
            from Brand b
            where lower(b.name) like cast(function('likequery', lower(:name)) as string) escape '\\'
            order by length(b.name) desc, b.id asc
            """)
    List<Brand> findByNameLike(@Param("name") String name);

    List<Brand> findByNameIn(Collection<String> names);
}
