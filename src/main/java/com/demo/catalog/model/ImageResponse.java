package com.demo.catalog.model;

import java.util.List;

public record ImageResponse(
        String imageId,
        String originalUrl,
        String thumbnailUrl,
        List<String> tags,
        String description,
        String status,
        String rejectionReason,
        String uploadedAt,
        String processedAt
) {
}
