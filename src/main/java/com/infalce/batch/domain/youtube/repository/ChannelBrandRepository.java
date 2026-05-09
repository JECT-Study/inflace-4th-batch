package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.channel.ChannelBrand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChannelBrandRepository extends JpaRepository<ChannelBrand, Long> {

    List<ChannelBrand> findByChannelIdIn(Collection<Long> channelIds);
}
