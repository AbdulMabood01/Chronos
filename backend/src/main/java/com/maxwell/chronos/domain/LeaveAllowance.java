package com.maxwell.chronos.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_allowances", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "leave_year"}))
@Getter @Setter @NoArgsConstructor
public class LeaveAllowance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "leave_year", nullable = false)
    private int year;
    @Column(nullable = false) private BigDecimal vacationDays = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal bereavementDays = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal sickDays = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal extraVacationDays = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal extraSickDays = BigDecimal.ZERO;
}
