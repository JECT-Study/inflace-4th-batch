package com.infalce.batch.domain.youtube.model;

import java.util.List;

public record UploadMetrics(int recentUploadCount, List<String> recentVideoIds) {
}
