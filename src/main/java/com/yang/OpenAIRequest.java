package com.yang;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAIRequest {
    private Object frequency_penalty;
    private Object presence_penalty;
    private boolean stream;
    private String model;
    private Object messages;
    private Object temperature;
    private Object top_p;
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Object getFrequency_penalty() {
        return frequency_penalty;
    }

    public void setFrequency_penalty(Object frequency_penalty) {
        this.frequency_penalty = frequency_penalty;
    }

    public Object getPresence_penalty() {
        return presence_penalty;
    }

    public void setPresence_penalty(Object presence_penalty) {
        this.presence_penalty = presence_penalty;
    }

    public boolean getStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public Object getMessages() {
        return messages;
    }

    public void setMessages(Object messages) {
        this.messages = messages;
    }

    public Object getTemperature() {
        return temperature;
    }

    public void setTemperature(Object temperature) {
        this.temperature = temperature;
    }

    public Object getTop_p() {
        return top_p;
    }

    public void setTop_p(Object top_p) {
        this.top_p = top_p;
    }

    public boolean isGPT4Model() {
        if (model == null) {
            return false;
        }
        return model.contains("gpt-4");
    }
}
