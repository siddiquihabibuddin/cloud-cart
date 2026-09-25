package com.cloudcart.agent.client;

import com.cloudcart.agent.model.ChatCompletionResponse;
import com.cloudcart.agent.model.Message;

import java.util.List;
import java.util.Map;

public interface GroqClient {

    ChatCompletionResponse complete(List<Message> messages, List<Map<String, Object>> tools) throws Exception;
}
