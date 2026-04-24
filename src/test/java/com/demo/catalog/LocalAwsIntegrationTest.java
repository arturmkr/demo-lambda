package com.demo.catalog;

import com.demo.catalog.model.ImageResponse;
import com.demo.catalog.model.UploadImageResponse;
import com.demo.catalog.service.ImageCatalogService;
import com.demo.catalog.service.RekognitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "app.aws.region=us-east-1",
        "app.aws.access-key=test",
        "app.aws.secret-key=test",
        "app.aws.s3.endpoint=http://localhost:4566",
        "app.aws.dynamodb.endpoint=http://localhost:4566",
        "app.aws.rekognition.endpoint=http://localhost:4566",
        "app.aws.s3.path-style-access=true",
        "app.images.bucket=image-catalog-test-bucket",
        "app.images.table=ImagesTest",
        "app.images.enable-moderation=false"
})
class LocalAwsIntegrationTest {

    @TestConfiguration
    static class RekognitionMockConfig {

        @Bean
        @Primary
        RekognitionService rekognitionService() {
            return mock(RekognitionService.class);
        }
    }

    @Autowired
    private ImageCatalogService imageCatalogService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private RekognitionService rekognitionService;

    @BeforeEach
    void setUpInfrastructure() {
        createBucketIfMissing("image-catalog-test-bucket");
        recreateTable("ImagesTest");
    }

    @Test
    void uploadAndProcessApprovedImageShouldBeSearchable() throws IOException, InterruptedException {
        when(rekognitionService.detectLabels(anyString())).thenReturn(List.of("tea", "cup", "drink"));
        when(rekognitionService.detectModerationLabels(anyString())).thenReturn(List.of());
        when(rekognitionService.containsBannedLabels(anyList())).thenReturn(false);

        byte[] imageBytes = Files.readAllBytes(Path.of("images/tea1.jpg"));
        UploadImageResponse upload = imageCatalogService.upload(imageBytes, "image/jpeg");

        imageCatalogService.process("original/" + upload.imageId() + ".jpg");

        ImageResponse stored = imageCatalogService.getById(upload.imageId());
        assertThat(stored.status()).isEqualTo("APPROVED");
        assertThat(stored.tags()).contains("tea", "cup", "drink");
        assertThat(stored.thumbnailUrl()).isNotBlank();

        List<ImageResponse> results = imageCatalogService.searchByTags(List.of("tea"));
        assertThat(results).extracting(ImageResponse::imageId).contains(upload.imageId());
    }

    @Test
    void uploadAndProcessRejectedImageShouldNotAppearInSearch() throws IOException {
        when(rekognitionService.detectLabels(anyString())).thenReturn(List.of("knife", "weapon"));
        when(rekognitionService.detectModerationLabels(anyString())).thenReturn(List.of());
        when(rekognitionService.containsBannedLabels(anyList())).thenReturn(true);

        byte[] imageBytes = Files.readAllBytes(Path.of("images/knife1.jpg"));
        UploadImageResponse upload = imageCatalogService.upload(imageBytes, "image/jpeg");

        imageCatalogService.process("original/" + upload.imageId() + ".jpg");

        ImageResponse stored = imageCatalogService.getById(upload.imageId());
        assertThat(stored.status()).isEqualTo("REJECTED");
        assertThat(stored.rejectionReason()).contains("banned");

        List<ImageResponse> results = imageCatalogService.searchByTags(List.of("knife"));
        assertThat(results).extracting(ImageResponse::imageId).doesNotContain(upload.imageId());
    }

    private void createBucketIfMissing(String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception ignored) {
        }
    }

    private void recreateTable(String tableName) {
        try {
            dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
            waitForTableDeletion(tableName);
        } catch (ResourceNotFoundException ignored) {
        }

        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("imageId")
                            .attributeType("S")
                            .build())
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("imageId")
                            .keyType(KeyType.HASH)
                            .build())
                    .build());
        } catch (ResourceInUseException ignored) {
        }
    }

    private void waitForTableDeletion(String tableName) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                dynamoDbClient.describeTable(builder -> builder.tableName(tableName));
                Thread.sleep(250);
            } catch (ResourceNotFoundException exception) {
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
