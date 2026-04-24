package com.demo.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.function.context.FunctionCatalog;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "AWS_REGION=us-east-1",
        "app.aws.region=us-east-1",
        "app.images.bucket=test-bucket",
        "app.images.table=test-table"
})
class FunctionCatalogIntegrationTest {

    @Autowired
    private FunctionCatalog functionCatalog;

    @Test
    void shouldRegisterLambdaFunctions() {
        assertThat((Object) functionCatalog.lookup("uploadImage")).isNotNull();
        assertThat((Object) functionCatalog.lookup("searchImages")).isNotNull();
        assertThat((Object) functionCatalog.lookup("getImage")).isNotNull();
        assertThat((Object) functionCatalog.lookup("processImage")).isNotNull();
    }
}
