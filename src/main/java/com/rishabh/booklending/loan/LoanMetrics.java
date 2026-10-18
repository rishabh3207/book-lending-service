package com.rishabh.booklending.loan;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
class LoanMetrics implements MeterBinder {

    private final LoanRepository loans;
    private final Clock clock;

    LoanMetrics(LoanRepository loans, Clock clock) {
        this.loans = loans;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("library.loans.active", loans, LoanRepository::countByReturnedAtIsNull)
                .description("Loans not yet returned")
                .register(registry);
        Gauge.builder("library.loans.overdue", loans, r -> r.countByReturnedAtIsNullAndDueDateBefore(clock.instant()))
                .description("Loans not returned and past their due date")
                .register(registry);
    }
}
