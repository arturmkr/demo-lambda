package com.demo.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsClientConfig {

    @Bean
    public Region awsRegion() {
        return DefaultAwsRegionProviderChain.builder().build().getRegion();
    }

    @Bean
    public S3Client s3Client(Region region) {
        return S3Client.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(Region region) {
        return S3Presigner.builder()
                .region(region)
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(Region region) {
        return DynamoDbClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    public RekognitionClient rekognitionClient(Region region) {
        return RekognitionClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
