package com.cloudcart.mcp.tool;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductToolsTest {

    @Test
    void getProductReturnsParsedBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/products/p1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"productId\":\"p1\",\"title\":\"Mouse\",\"price\":19.99}",
                        MediaType.APPLICATION_JSON));

        ProductTools tools = new ProductTools(builder.build());
        Map<String, Object> result = tools.getProduct("p1");

        assertThat(result).containsEntry("productId", "p1").containsEntry("title", "Mouse");
        server.verify();
    }

    @Test
    void searchProductsFiltersByMaxPrice() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/search?q=laptop"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"products\":[{\"productId\":\"p1\",\"price\":750},{\"productId\":\"p2\",\"price\":900}],\"total\":2}",
                        MediaType.APPLICATION_JSON));

        ProductTools tools = new ProductTools(builder.build());
        Map<String, Object> result = tools.searchProducts("laptop", 800.0);

        assertThat(result.get("total")).isEqualTo(1);
        server.verify();
    }

    @Test
    void getProductReturnsErrorMapOnFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/products/missing"))
                .andExpect(method(GET))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        ProductTools tools = new ProductTools(builder.build());
        Map<String, Object> result = tools.getProduct("missing");

        assertThat(result).containsKey("error");
        server.verify();
    }
}
