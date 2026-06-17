package com.demo.catalog.service;

import com.demo.catalog.config.AppProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Map;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AppProperties appProperties;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, AppProperties appProperties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.appProperties = appProperties;
    }

    public void upload(String key, byte[] content, String contentType) {
        upload(key, content, contentType, Map.of());
    }

    public void upload(String key, byte[] content, String contentType, Map<String, String> metadata) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(appProperties.getImageBucket())
                        .key(key)
                        .contentType(contentType)
                        .metadata(metadata)
                        .build(),
                RequestBody.fromBytes(content));
    }

    public Map<String, String> metadata(String key) {
        return s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(appProperties.getImageBucket())
                        .key(key)
                        .build())
                .metadata();
    }

    public byte[] download(String key) {
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(appProperties.getImageBucket())
                .key(key)
                .build());
        return response.asByteArray();
    }

    public String presignGetUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        try {
            return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(appProperties.getPresignedMinutes()))
                            .getObjectRequest(request -> request
                                    .bucket(appProperties.getImageBucket())
                                    .key(key))
                            .build())
                    .url()
                    .toString();
        } catch (S3Exception exception) {
            return null;
        }
    }
}
