package com.cloudcart.mcp.tool;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CartToolsTest {

    @Test
    void addToCartSendsExpectedBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/cart"))
                .andExpect(method(POST))
                .andExpect(content().json(
                        "{\"userId\":\"u1\",\"productId\":\"p1\",\"title\":\"Mouse\",\"price\":19.99,\"quantity\":2}"))
                .andRespond(withSuccess("{\"message\":\"Item added to cart\"}", MediaType.APPLICATION_JSON));

        CartTools tools = new CartTools(builder.build());
        Map<String, Object> result = tools.addToCart("u1", "p1", "Mouse", 19.99, 2);

        assertThat(result).containsEntry("message", "Item added to cart");
        server.verify();
    }

    @Test
    void viewCartReturnsListOfItems() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/cart/u1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[{\"productId\":\"p1\",\"quantity\":2}]", MediaType.APPLICATION_JSON));

        CartTools tools = new CartTools(builder.build());
        List<Map<String, Object>> result = tools.viewCart("u1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("productId", "p1");
        server.verify();
    }

    @Test
    void updateCartQuantitySendsPatch() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/cart/u1/p1"))
                .andExpect(method(PATCH))
                .andExpect(content().json("{\"quantity\":3}"))
                .andRespond(withSuccess("{\"message\":\"Quantity updated\"}", MediaType.APPLICATION_JSON));

        CartTools tools = new CartTools(builder.build());
        Map<String, Object> result = tools.updateCartQuantity("u1", "p1", 3);

        assertThat(result).containsEntry("message", "Quantity updated");
        server.verify();
    }

    @Test
    void removeFromCartSendsDelete() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/cart/u1/p1"))
                .andExpect(method(DELETE))
                .andRespond(withSuccess("{\"message\":\"Item removed from cart\"}", MediaType.APPLICATION_JSON));

        CartTools tools = new CartTools(builder.build());
        Map<String, Object> result = tools.removeFromCart("u1", "p1");

        assertThat(result).containsEntry("message", "Item removed from cart");
        server.verify();
    }
}
