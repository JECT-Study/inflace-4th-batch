package com.infalce.batch.entity.brand;

import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brand_alias", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"brand_id", "alias"})
})
@Getter
public class BrandAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "alias", nullable = false)
    private String alias;
}
