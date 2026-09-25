package com.cloudcart.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionResponse {

    private List<Choice> choices;

    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }
}
