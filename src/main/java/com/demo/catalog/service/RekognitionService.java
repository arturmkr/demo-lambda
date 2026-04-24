package com.demo.catalog.service;

import com.demo.catalog.config.AppProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;
import software.amazon.awssdk.services.rekognition.model.S3Object;

import java.util.List;
import java.util.Locale;

@Service
public class RekognitionService {

    private final RekognitionClient rekognitionClient;
    private final AppProperties appProperties;

    public RekognitionService(RekognitionClient rekognitionClient, AppProperties appProperties) {
        this.rekognitionClient = rekognitionClient;
        this.appProperties = appProperties;
    }

    public List<String> detectLabels(String s3Key) {
        return rekognitionClient.detectLabels(DetectLabelsRequest.builder()
                        .image(s3Image(s3Key))
                        .maxLabels(appProperties.getMaxLabels())
                        .minConfidence(appProperties.getMinConfidence())
                        .build())
                .labels()
                .stream()
                .map(label -> label.name().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public boolean containsBannedLabels(List<String> tags) {
        return tags.stream().anyMatch(tag -> appProperties.getBannedLabels().contains(tag));
    }

    public List<String> detectModerationLabels(String s3Key) {
        if (!appProperties.isModerationEnabled()) {
            return List.of();
        }

        return rekognitionClient.detectModerationLabels(DetectModerationLabelsRequest.builder()
                        .image(s3Image(s3Key))
                        .minConfidence(70f)
                        .build())
                .moderationLabels()
                .stream()
                .map(this::toModerationText)
                .distinct()
                .toList();
    }

    private Image s3Image(String key) {
        return Image.builder()
                .s3Object(S3Object.builder()
                        .bucket(appProperties.getImageBucket())
                        .name(key)
                        .build())
                .build();
    }

    private String toModerationText(ModerationLabel moderationLabel) {
        return moderationLabel.parentName() == null || moderationLabel.parentName().isBlank()
                ? moderationLabel.name()
                : moderationLabel.parentName() + "/" + moderationLabel.name();
    }
}
