package com.core.listeners;

import com.core.events.ReportRequestedEvent;
import com.core.services.ReportGenerationProcessorService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportRequestedListener {

    private final ReportGenerationProcessorService reportGenerationProcessorService;

    @Async("reportTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReportRequestedEvent event) {
        reportGenerationProcessorService.process(event);
    }
}
