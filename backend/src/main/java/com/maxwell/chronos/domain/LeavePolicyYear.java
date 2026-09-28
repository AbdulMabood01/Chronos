package com.maxwell.chronos.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_policy_years")
@Getter @Setter
public class LeavePolicyYear {
    @Id private int year;
    private BigDecimal vacationDays;
    private BigDecimal sickDays;
    private BigDecimal bereavementDays;
}
