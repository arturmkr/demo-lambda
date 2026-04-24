package com.demo.catalog.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.demo.catalog.model.ImageResponse;
import com.demo.catalog.model.UploadImageRequest;
import com.demo.catalog.service.ImageCatalogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

@Configuration
public class ApiHandlers {

    private final ImageCatalogService imageCatalogService;
    private final ObjectMapper objectMapper;

    public ApiHandlers(ImageCatalogService imageCatalogService, ObjectMapper objectMapper) {
        this.imageCatalogService = imageCatalogService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> uploadImage() {
        return request -> {
            try {
                byte[] imageBytes = resolveUploadBody(request);
                String contentType = resolveContentType(request);
                return ok(imageCatalogService.upload(imageBytes, contentType));
            } catch (Exception exception) {
                return error(400, exception.getMessage());
            }
        };
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> searchImages() {
        return request -> {
            try {
                String tagParam = request.getQueryStringParameters() == null
                        ? ""
                        : request.getQueryStringParameters().getOrDefault("tags", "");

                List<String> tags = StringUtils.isBlank(tagParam)
                        ? List.of()
                        : List.of(tagParam.split(","));

                return ok(imageCatalogService.searchByTags(tags));
            } catch (Exception exception) {
                return error(500, exception.getMessage());
            }
        };
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> getImage() {
        return request -> {
            try {
                String imageId = request.getPathParameters() == null
                        ? null
                        : request.getPathParameters().get("imageId");

                if (StringUtils.isBlank(imageId)) {
                    return error(400, "Missing imageId path parameter");
                }

                ImageResponse response = imageCatalogService.getById(imageId);
                return ok(response);
            } catch (NoSuchElementException exception) {
                return error(404, exception.getMessage());
            } catch (Exception exception) {
                return error(500, exception.getMessage());
            }
        };
    }

    private byte[] resolveUploadBody(APIGatewayProxyRequestEvent request) throws JsonProcessingException {
        String contentType = resolveContentType(request);
        if (contentType.contains("application/json")) {
            UploadImageRequest payload = objectMapper.readValue(request.getBody(), UploadImageRequest.class);
            if (payload.base64Data() == null) {
                throw new IllegalArgumentException("JSON body must include base64Data");
            }
            return Base64.getDecoder().decode(payload.base64Data());
        }

        String body = request.getBody();
        if (!Boolean.TRUE.equals(request.getIsBase64Encoded()) && body != null && contentType.startsWith("image/")) {
            return body.getBytes(StandardCharsets.ISO_8859_1);
        }
        return imageCatalogService.decodeImageBody(body, Boolean.TRUE.equals(request.getIsBase64Encoded()));
    }

    private String resolveContentType(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers == null) {
            return "image/jpeg";
        }
        return headers.entrySet().stream()
                .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("image/jpeg");
    }

    private APIGatewayProxyResponseEvent ok(Object body) {
        return jsonResponse(200, body);
    }

    private APIGatewayProxyResponseEvent error(int statusCode, String message) {
        return jsonResponse(statusCode, Map.of("error", message));
    }

    private APIGatewayProxyResponseEvent jsonResponse(int statusCode, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(objectMapper.writeValueAsString(body))
                    .withIsBase64Encoded(false);
        } catch (JsonProcessingException exception) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"error\":\"Failed to serialize response\"}")
                    .withIsBase64Encoded(false);
        }
    }
}