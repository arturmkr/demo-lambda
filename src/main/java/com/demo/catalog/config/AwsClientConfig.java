package com.demo.catalog.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.RekognitionClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder;

import java.net.URI;

@Configuration
public class AwsClientConfig {

    @Bean
    public Region awsRegion(@Value("${app.aws.region:}") String configuredRegion) {
        if (StringUtils.isNotBlank(configuredRegion)) {
            return Region.of(configuredRegion);
        }
        return DefaultAwsRegionProviderChain.builder().build().getRegion();
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(
            @Value("${app.aws.access-key:}") String accessKey,
            @Value("${app.aws.secret-key:}") String secretKey
    ) {
        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            @Value("${app.aws.s3.endpoint:}") String endpoint,
            @Value("${app.aws.s3.path-style-access:false}") boolean pathStyleAccess
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());
        applyEndpoint(builder, endpoint);
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            @Value("${app.aws.s3.endpoint:}") String endpoint
    ) {
        Builder builder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider);
        applyEndpoint(builder, endpoint);
        return builder.build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            @Value("${app.aws.dynamodb.endpoint:}") String endpoint
    ) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        applyEndpoint(builder, endpoint);
        return builder.build();
    }

    @Bean
    public RekognitionClient rekognitionClient(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            @Value("${app.aws.rekognition.endpoint:}") String endpoint
    ) {
        RekognitionClientBuilder builder = RekognitionClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        applyEndpoint(builder, endpoint);
        return builder.build();
    }

    private void applyEndpoint(S3ClientBuilder builder, String endpoint) {
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }

    private void applyEndpoint(Builder builder, String endpoint) {
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }

    private void applyEndpoint(DynamoDbClientBuilder builder, String endpoint) {
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }

    private void applyEndpoint(RekognitionClientBuilder builder, String endpoint) {
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
    }
}
