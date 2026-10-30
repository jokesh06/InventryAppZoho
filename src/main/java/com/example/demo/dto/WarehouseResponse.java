package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseResponse {
    private Long id;
    private String code;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}