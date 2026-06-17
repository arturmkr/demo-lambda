package com.demo.catalog.function;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.demo.catalog.model.ImageResponse;
import com.demo.catalog.model.UploadImageRequest;
import com.demo.catalog.service.ImageCatalogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

@Configuration
public class ApiHandlers {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiHandlers.class);
    private static final String USER_GROUP = "USER";
    private static final String ADMIN_GROUP = "ADMIN";

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
                AuthenticatedUser authenticatedUser = authorize(request, Set.of(USER_GROUP, ADMIN_GROUP));
                String reqContentType = resolveContentType(request);
                byte[] imageBytes;
                String imageContentType;

                if (reqContentType.contains("application/json")) {
                    UploadImageRequest payload = objectMapper.readValue(request.getBody(), UploadImageRequest.class);
                    if (payload.base64Data() == null) {
                        throw new IllegalArgumentException("JSON body must include base64Data");
                    }
                    imageBytes = Base64.getDecoder().decode(payload.base64Data());
                    imageContentType = StringUtils.isBlank(payload.contentType()) ? "image/jpeg" : payload.contentType();
                } else {
                    String body = request.getBody();
                    boolean isBase64 = Boolean.TRUE.equals(request.getIsBase64Encoded());
                    if (!isBase64 && body != null && reqContentType.startsWith("image/")) {
                        imageBytes = body.getBytes(StandardCharsets.ISO_8859_1);
                    } else {
                        imageBytes = imageCatalogService.decodeImageBody(body, isBase64);
                    }
                    imageContentType = reqContentType;
                }

                return ok(imageCatalogService.upload(
                        imageBytes,
                        imageContentType,
                        authenticatedUser.sub(),
                        authenticatedUser.email()
                ));
            } catch (AccessDeniedException exception) {
                return error(exception.statusCode(), exception.getMessage());
            } catch (Exception exception) {
                return error(400, exception.getMessage());
            }
        };
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> listMyImages() {
        return request -> {
            try {
                AuthenticatedUser authenticatedUser = authorize(request, Set.of(USER_GROUP, ADMIN_GROUP));
                return ok(imageCatalogService.listByUploaderSub(authenticatedUser.sub()));
            } catch (AccessDeniedException exception) {
                return error(exception.statusCode(), exception.getMessage());
            } catch (Exception exception) {
                return error(500, exception.getMessage());
            }
        };
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> listAllImages() {
        return request -> {
            try {
                authorize(request, Set.of(ADMIN_GROUP));
                return ok(imageCatalogService.listAll());
            } catch (AccessDeniedException exception) {
                return error(exception.statusCode(), exception.getMessage());
            } catch (Exception exception) {
                return error(500, exception.getMessage());
            }
        };
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> approveImage() {
        return request -> decide(request, true);
    }

    @Bean
    public Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> rejectImage() {
        return request -> decide(request, false);
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

    private APIGatewayProxyResponseEvent decide(APIGatewayProxyRequestEvent request, boolean approved) {
        try {
            AuthenticatedUser authenticatedUser = authorize(request, Set.of(ADMIN_GROUP));
            String imageId = request.getPathParameters() == null
                    ? null
                    : request.getPathParameters().get("id");

            if (StringUtils.isBlank(imageId)) {
                return error(400, "Missing id path parameter");
            }

            return ok(approved
                    ? imageCatalogService.approve(imageId, authenticatedUser.sub(), authenticatedUser.email())
                    : imageCatalogService.reject(imageId, authenticatedUser.sub(), authenticatedUser.email()));
        } catch (AccessDeniedException exception) {
            return error(exception.statusCode(), exception.getMessage());
        } catch (NoSuchElementException exception) {
            return error(404, exception.getMessage());
        } catch (Exception exception) {
            return error(500, exception.getMessage());
        }
    }

    private AuthenticatedUser authorize(APIGatewayProxyRequestEvent request, Set<String> allowedGroups) {
        Map<String, String> claims = claims(request);
        String sub = claims.getOrDefault("sub", "");
        String email = claims.getOrDefault("email", "");
        Set<String> groups = groups(claims.get("cognito:groups"));

        if (StringUtils.isBlank(sub)) {
            LOGGER.warn("Unauthorized request without Cognito subject");
            throw new AccessDeniedException(401, "Unauthorized");
        }

        LOGGER.info("Authenticated request userSub={} userEmail={} groups={}", sub, email, groups);

        boolean allowed = groups.stream().anyMatch(allowedGroups::contains);
        if (!allowed) {
            LOGGER.warn("Forbidden access attempt userSub={} userEmail={} groups={} requiredGroups={}",
                    sub, email, groups, allowedGroups);
            throw new AccessDeniedException(403, "Forbidden");
        }

        return new AuthenticatedUser(sub, email, groups);
    }

    private Map<String, String> claims(APIGatewayProxyRequestEvent request) {
        if (request.getRequestContext() == null || request.getRequestContext().getAuthorizer() == null) {
            return Map.of();
        }

        Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
        Object jwt = authorizer.get("jwt");
        if (jwt instanceof Map<?, ?> jwtMap) {
            Object jwtClaims = jwtMap.get("claims");
            if (jwtClaims instanceof Map<?, ?> claimsMap) {
                return stringMap(claimsMap);
            }
        }

        Object claims = authorizer.get("claims");
        if (claims instanceof Map<?, ?> claimsMap) {
            return stringMap(claimsMap);
        }

        return stringMap(authorizer);
    }

    private Map<String, String> stringMap(Map<?, ?> source) {
        return source.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> valueAsString(entry.getValue())
                ));
    }

    private String valueAsString(Object value) {
        if (value instanceof Collection<?> collection) {
            return String.join(",", collection.stream().map(Object::toString).toList());
        }
        return value.toString();
    }

    private Set<String> groups(String claimValue) {
        if (StringUtils.isBlank(claimValue)) {
            return Set.of();
        }

        String normalized = claimValue.replace("[", "").replace("]", "").replace("\"", "");
        Set<String> result = new HashSet<>();
        Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .forEach(result::add);
        return result;
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

    private record AuthenticatedUser(String sub, String email, Set<String> groups) {
    }

    private static final class AccessDeniedException extends RuntimeException {
        private final int statusCode;

        private AccessDeniedException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
