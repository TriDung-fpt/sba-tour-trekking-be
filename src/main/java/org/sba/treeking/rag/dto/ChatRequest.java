package org.sba.treeking.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming chat request from the client.
 *
 * @param question  The user's natural-language question
 * @param sessionId Optional session ID for multi-turn context (future use)
 */
public record ChatRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question,

        String sessionId
) {}
