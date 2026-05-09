package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.brand.BrandAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BrandAliasRepository extends JpaRepository<BrandAlias, Long> {

    @Query("""
            select a
            from BrandAlias a
            join fetch a.brand
            order by a.id asc
            """)
    List<BrandAlias> findAllWithBrand();
}
