package com.demo.catalog.model;

public record UploadImageRequest(
        String fileName,
        String contentType,
        String base64Data
) {
}
