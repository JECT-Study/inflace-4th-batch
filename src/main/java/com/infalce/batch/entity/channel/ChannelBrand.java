package com.infalce.batch.entity.channel;

import com.infalce.batch.entity.brand.Brand;
import com.infalce.batch.entity.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "channel_brand",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_channel_brand",
                columnNames = {"channel_id", "brand_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelBrand extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "matched_alias", nullable = false)
    private String matchedAlias;

    public static ChannelBrand of(Channel channel, Brand brand, String matchedAlias) {
        ChannelBrand relation = new ChannelBrand();
        relation.channel = channel;
        relation.brand = brand;
        relation.matchedAlias = matchedAlias;
        return relation;
    }

    public boolean update(String matchedAlias) {
        if (Objects.equals(this.matchedAlias, matchedAlias)) {
            return false;
        }

        this.matchedAlias = matchedAlias;
        return true;
    }
}
