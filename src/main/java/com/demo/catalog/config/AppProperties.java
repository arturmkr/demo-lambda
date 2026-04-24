package com.demo.catalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppProperties {

    @Value("${app.images.bucket}")
    private String imageBucket;

    @Value("${app.images.table}")
    private String imagesTable;

    @Value("${app.images.original-prefix:original/}")
    private String originalPrefix;

    @Value("${app.images.thumbnail-prefix:thumbnail/}")
    private String thumbnailPrefix;

    @Value("${app.images.presigned-minutes:15}")
    private long presignedMinutes;

    @Value("${app.images.max-labels:10}")
    private int maxLabels;

    @Value("${app.images.min-confidence:75}")
    private float minConfidence;

    @Value("${app.images.enable-moderation:true}")
    private boolean moderationEnabled;

    public String getImageBucket() {
        return imageBucket;
    }

    public String getImagesTable() {
        return imagesTable;
    }

    public String getOriginalPrefix() {
        return originalPrefix;
    }

    public String getThumbnailPrefix() {
        return thumbnailPrefix;
    }

    public long getPresignedMinutes() {
        return presignedMinutes;
    }

    public int getMaxLabels() {
        return maxLabels;
    }

    public float getMinConfidence() {
        return minConfidence;
    }

    public boolean isModerationEnabled() {
        return moderationEnabled;
    }

    public List<String> getBannedLabels() {
        return List.of("weapon", "gun", "knife", "rifle", "pistol", "sword");
    }
}
