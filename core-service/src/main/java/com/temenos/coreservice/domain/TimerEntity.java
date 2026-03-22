package com.temenos.coreservice.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("timer")
public class TimerEntity {

    @Id
    @Column("timer_id")
    private UUID timerId;

    @NonNull
    @Column("created_at")
    private Long createdAt;

    @NonNull
    @Column("delay")
    private Integer delay;

    @Column("status")
    private TimerStatus status;

    @Column("attempts")
    private Integer attempts;

    @Column("updated_at")
    private Long updatedAt;
}