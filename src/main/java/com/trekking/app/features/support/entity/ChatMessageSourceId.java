package com.trekking.app.features.support.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatMessageSourceId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long messageId;

    private Long documentId;
}
