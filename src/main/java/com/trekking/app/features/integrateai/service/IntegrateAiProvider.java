package com.trekking.app.features.integrateai.service;

import java.util.List;
import java.util.Map;

public interface IntegrateAiProvider {
    Map<String, Object> select(String question, List<Map<String, Object>> recentTurns,
                               List<Map<String, Object>> tourCatalog);
}
