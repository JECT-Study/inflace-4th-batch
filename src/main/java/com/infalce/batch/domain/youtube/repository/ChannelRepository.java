package com.infalce.batch.domain.youtube.repository;

import com.infalce.batch.entity.channel.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByYoutubeChannelId(String youtubeChannelId);

    List<Channel> findByYoutubeChannelIdIn(Collection<String> youtubeChannelIds);

    List<Channel> findAllByOrderByIdAsc();

    @Query("select min(c.id) from Channel c where c.youtubeChannelId is not null")
    Long findMinIdByYoutubeChannelIdIsNotNull();

    @Query("select max(c.id) from Channel c where c.youtubeChannelId is not null")
    Long findMaxIdByYoutubeChannelIdIsNotNull();
}
