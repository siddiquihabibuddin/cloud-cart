package com.cloudcart.mcp.tool;

import com.cloudcart.mcp.config.CloudCartProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrderToolsTest {

    private static final CloudCartProperties PROPERTIES =
            new CloudCartProperties("http://gateway.test", "test-api-key");

    @Test
    void placeOrderSendsApiKeyHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/orders"))
                .andExpect(method(POST))
                .andExpect(header("x-api-key", "test-api-key"))
                .andRespond(withSuccess("{\"orderId\":\"o1\"}", MediaType.APPLICATION_JSON));

        OrderTools tools = new OrderTools(builder.build(), PROPERTIES);
        Map<String, Object> result = tools.placeOrder("u1", List.of(new OrderItemInput("p1", 2, 19.99)));

        assertThat(result).containsEntry("orderId", "o1");
        server.verify();
    }

    @Test
    void getOrderSendsUserIdQueryParamAndApiKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/orders/o1?userId=u1"))
                .andExpect(method(GET))
                .andExpect(header("x-api-key", "test-api-key"))
                .andRespond(withSuccess("{\"orderId\":\"o1\",\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        OrderTools tools = new OrderTools(builder.build(), PROPERTIES);
        Map<String, Object> result = tools.getOrder("o1", "u1");

        assertThat(result).containsEntry("status", "PENDING");
        server.verify();
    }

    @Test
    void listOrdersReturnsList() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway.test/orders?userId=u1"))
                .andExpect(method(GET))
                .andExpect(header("x-api-key", "test-api-key"))
                .andRespond(withSuccess("[{\"orderId\":\"o1\"}]", MediaType.APPLICATION_JSON));

        OrderTools tools = new OrderTools(builder.build(), PROPERTIES);
        List<Map<String, Object>> result = tools.listOrders("u1");

        assertThat(result).hasSize(1);
        server.verify();
    }
}
