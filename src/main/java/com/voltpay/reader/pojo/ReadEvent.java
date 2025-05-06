package com.voltpay.reader.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ReadEvent {

    private String messageId;

    private String id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private BigDecimal amount;

    private Integer status;

    private String currency;

    private Long custId;

    private String type;

    private String comment;

    private Integer version;
}
