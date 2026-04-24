package com.demo.catalog.service;

import com.demo.catalog.config.AppProperties;
import com.demo.catalog.model.ImageMetadata;
import com.demo.catalog.model.ImageStatus;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDbImageRepository {

    private final DynamoDbClient dynamoDbClient;
    private final AppProperties appProperties;

    public DynamoDbImageRepository(DynamoDbClient dynamoDbClient, AppProperties appProperties) {
        this.dynamoDbClient = dynamoDbClient;
        this.appProperties = appProperties;
    }

    public void save(ImageMetadata imageMetadata) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(appProperties.getImagesTable())
                .item(toItem(imageMetadata))
                .build());
    }

    public Optional<ImageMetadata> findById(String imageId) {
        Map<String, AttributeValue> key = Map.of(
                "imageId", AttributeValue.builder().s(imageId).build()
        );

        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(appProperties.getImagesTable())
                .key(key)
                .build()).item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fromItem(item));
    }

    public List<ImageMetadata> searchApprovedByTags(List<String> tags) {
        Map<String, AttributeValue> values = new HashMap<>();
        StringBuilder filter = new StringBuilder("#status = :approved");
        Map<String, String> names = Map.of("#status", "status");
        values.put(":approved", AttributeValue.builder().s(ImageStatus.APPROVED.name()).build());

        for (int index = 0; index < tags.size(); index++) {
            String key = ":tag" + index;
            filter.append(" AND contains(tags, ").append(key).append(")");
            values.put(key, AttributeValue.builder().s(tags.get(index).toLowerCase(Locale.ROOT)).build());
        }

        ScanRequest request = ScanRequest.builder()
                .tableName(appProperties.getImagesTable())
                .filterExpression(filter.toString())
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();

        List<ImageMetadata> result = new ArrayList<>();
        dynamoDbClient.scanPaginator(request).items().forEach(item -> result.add(fromItem(item)));
        return result;
    }

    private Map<String, AttributeValue> toItem(ImageMetadata imageMetadata) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("imageId", AttributeValue.builder().s(imageMetadata.getImageId()).build());
        item.put("status", AttributeValue.builder().s(imageMetadata.getStatus().name()).build());
        item.put("originalKey", AttributeValue.builder().s(nullSafe(imageMetadata.getOriginalKey())).build());
        item.put("thumbnailKey", AttributeValue.builder().s(nullSafe(imageMetadata.getThumbnailKey())).build());
        item.put("description", AttributeValue.builder().s(nullSafe(imageMetadata.getDescription())).build());
        item.put("rejectionReason", AttributeValue.builder().s(nullSafe(imageMetadata.getRejectionReason())).build());
        item.put("uploadedAt", AttributeValue.builder().s(nullSafe(imageMetadata.getUploadedAt())).build());
        item.put("processedAt", AttributeValue.builder().s(nullSafe(imageMetadata.getProcessedAt())).build());

        List<AttributeValue> tagValues = imageMetadata.getTags() == null
                ? List.of()
                : imageMetadata.getTags().stream()
                .map(tag -> AttributeValue.builder().s(tag).build())
                .toList();
        item.put("tags", AttributeValue.builder().l(tagValues).build());
        return item;
    }

    private ImageMetadata fromItem(Map<String, AttributeValue> item) {
        ImageMetadata metadata = new ImageMetadata();
        metadata.setImageId(readString(item, "imageId"));
        metadata.setStatus(ImageStatus.valueOf(readString(item, "status")));
        metadata.setOriginalKey(readString(item, "originalKey"));
        metadata.setThumbnailKey(readString(item, "thumbnailKey"));
        metadata.setDescription(readString(item, "description"));
        metadata.setRejectionReason(readString(item, "rejectionReason"));
        metadata.setUploadedAt(readString(item, "uploadedAt"));
        metadata.setProcessedAt(readString(item, "processedAt"));

        List<String> tags = Optional.ofNullable(item.get("tags"))
                .map(AttributeValue::l)
                .orElse(List.of())
                .stream()
                .map(AttributeValue::s)
                .toList();
        metadata.setTags(tags);
        return metadata;
    }

    private String readString(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? null : value.s();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
