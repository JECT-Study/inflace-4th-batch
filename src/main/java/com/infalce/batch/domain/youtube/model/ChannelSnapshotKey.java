package com.infalce.batch.domain.youtube.model;

import java.time.LocalDate;

public record ChannelSnapshotKey(Long channelId, LocalDate snapshotDate) {
}
