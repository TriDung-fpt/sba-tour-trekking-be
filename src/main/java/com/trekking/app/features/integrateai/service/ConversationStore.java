package com.trekking.app.features.integrateai.service;

import java.util.List;

public interface ConversationStore {
    Conversation loadOrCreate(String requestedId, String owner);
    void save(Conversation conversation);
    long incrementRate(String clientKey);
    record Turn(String role, String content) {}
    record Conversation(String id, String owner, List<Turn> turns, List<Long> recentTourIds) {}
}
