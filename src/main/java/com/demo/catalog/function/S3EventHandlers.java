package com.demo.catalog.function;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.demo.catalog.service.ImageCatalogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Configuration
public class S3EventHandlers {

    private final ImageCatalogService imageCatalogService;

    public S3EventHandlers(ImageCatalogService imageCatalogService) {
        this.imageCatalogService = imageCatalogService;
    }

    @Bean
    public Consumer<S3Event> processImage() {
        return event -> event.getRecords().forEach(record -> {
            String key = URLDecoder.decode(record.getS3().getObject().getKey(), StandardCharsets.UTF_8);
            imageCatalogService.process(key);
        });
    }
}
