package com.payledger.platform.settlement.infrastructure;

import com.payledger.platform.settlement.domain.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementLineRepository
        extends JpaRepository<SettlementLine, UUID> {
}
