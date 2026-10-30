package com.example.demo.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    private Long id;

    private String idempotencyKey;

    private String endpoint;

    private String requestHash;

    private String responsePayload;

    private Integer statusCode;

    private LocalDateTime createdAt;
}