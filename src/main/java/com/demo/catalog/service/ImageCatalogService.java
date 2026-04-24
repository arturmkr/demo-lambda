package com.demo.catalog.service;

import com.demo.catalog.config.AppProperties;
import com.demo.catalog.model.ImageMetadata;
import com.demo.catalog.model.ImageResponse;
import com.demo.catalog.model.ImageStatus;
import com.demo.catalog.model.UploadImageResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ImageCatalogService {

    private final AppProperties appProperties;
    private final S3StorageService s3StorageService;
    private final DynamoDbImageRepository imageRepository;
    private final RekognitionService rekognitionService;
    private final ImageTransformService imageTransformService;

    public ImageCatalogService(AppProperties appProperties,
                               S3StorageService s3StorageService,
                               DynamoDbImageRepository imageRepository,
                               RekognitionService rekognitionService,
                               ImageTransformService imageTransformService) {
        this.appProperties = appProperties;
        this.s3StorageService = s3StorageService;
        this.imageRepository = imageRepository;
        this.rekognitionService = rekognitionService;
        this.imageTransformService = imageTransformService;
    }

    public UploadImageResponse upload(byte[] imageBytes, String contentType) {
        String imageId = "img-" + UUID.randomUUID().toString().substring(0, 8);
        String normalizedContentType = defaultContentType(contentType);
        String originalKey = appProperties.getOriginalPrefix() + imageId + extensionFor(normalizedContentType);

        s3StorageService.upload(originalKey, imageBytes, normalizedContentType);

        ImageMetadata metadata = new ImageMetadata();
        metadata.setImageId(imageId);
        metadata.setStatus(ImageStatus.PROCESSING);
        metadata.setOriginalKey(originalKey);
        metadata.setUploadedAt(Instant.now().toString());
        imageRepository.save(metadata);

        return new UploadImageResponse(imageId, ImageStatus.PROCESSING.name());
    }

    public void process(String s3Key) {
        if (!s3Key.startsWith(appProperties.getOriginalPrefix())) {
            return;
        }

        String imageId = extractImageId(s3Key);
        ImageMetadata metadata = imageRepository.findById(imageId)
                .orElseGet(() -> fallbackMetadata(imageId, s3Key));

        byte[] original = s3StorageService.download(s3Key);
        byte[] thumbnail = imageTransformService.createThumbnail(original);
        String thumbnailKey = appProperties.getThumbnailPrefix() + imageId + ".jpg";
        s3StorageService.upload(thumbnailKey, thumbnail, "image/jpeg");

        List<String> tags = rekognitionService.detectLabels(s3Key);
        List<String> moderationLabels = rekognitionService.detectModerationLabels(s3Key);

        metadata.setThumbnailKey(thumbnailKey);
        metadata.setTags(tags);
        metadata.setDescription(buildDescription(tags));
        metadata.setProcessedAt(Instant.now().toString());

        if (rekognitionService.containsBannedLabels(tags)) {
            metadata.setStatus(ImageStatus.REJECTED);
            metadata.setRejectionReason("Detected banned object label");
        } else if (!moderationLabels.isEmpty()) {
            metadata.setStatus(ImageStatus.REJECTED);
            metadata.setRejectionReason("Moderation labels detected: " + String.join(", ", moderationLabels));
        } else {
            metadata.setStatus(ImageStatus.APPROVED);
            metadata.setRejectionReason("");
        }

        imageRepository.save(metadata);
    }

    public List<ImageResponse> searchByTags(List<String> tags) {
        return imageRepository.searchApprovedByTags(normalizeTags(tags))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ImageResponse getById(String imageId) {
        return imageRepository.findById(imageId)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Image not found: " + imageId));
    }

    public byte[] decodeImageBody(String body, boolean base64Encoded) {
        if (StringUtils.isBlank(body)) {
            throw new IllegalArgumentException("Request body is empty");
        }

        return base64Encoded ? Base64.getDecoder().decode(body) : body.getBytes();
    }

    private List<String> normalizeTags(List<String> tags) {
        return tags.stream()
                .filter(StringUtils::isNotBlank)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private String extractImageId(String s3Key) {
        String fileName = s3Key.substring(appProperties.getOriginalPrefix().length());
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private ImageMetadata fallbackMetadata(String imageId, String originalKey) {
        ImageMetadata metadata = new ImageMetadata();
        metadata.setImageId(imageId);
        metadata.setOriginalKey(originalKey);
        metadata.setStatus(ImageStatus.PROCESSING);
        metadata.setUploadedAt(Instant.now().toString());
        return metadata;
    }

    private String buildDescription(List<String> tags) {
        if (tags.isEmpty()) {
            return "Image processed without confident labels";
        }
        return "Image contains " + String.join(", ", tags);
    }

    private ImageResponse toResponse(ImageMetadata metadata) {
        return new ImageResponse(
                metadata.getImageId(),
                s3StorageService.presignGetUrl(metadata.getOriginalKey()),
                s3StorageService.presignGetUrl(metadata.getThumbnailKey()),
                metadata.getTags(),
                metadata.getDescription(),
                metadata.getStatus().name(),
                metadata.getRejectionReason(),
                metadata.getUploadedAt(),
                metadata.getProcessedAt()
        );
    }

    private String defaultContentType(String contentType) {
        return StringUtils.isBlank(contentType) ? "image/jpeg" : contentType;
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
