package com.infalce.batch.domain.email.batch.writer;

import com.infalce.batch.domain.email.service.EmailSender;
import com.infalce.batch.entity.email.EmailOutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@RequiredArgsConstructor
public class EmailOutboxRelayItemWriter implements ItemWriter<EmailOutboxEvent> {

    private final EmailSender emailSender;
    private final ItemWriter<EmailOutboxEvent> delegate;
    private final Executor executor;

    @Override
    public void write(Chunk<? extends EmailOutboxEvent> chunk) throws Exception {
        List<EmailSendTask> tasks = chunk.getItems().stream()
                .map(this::prepareSendTask)
                .toList();

        List<CompletableFuture<EmailSendResult>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> sendEmail(task), executor))
                .toList();

        List<EmailSendResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        for (int i = 0; i < chunk.size(); i++) {
            EmailOutboxEvent event = chunk.getItems().get(i);
            EmailSendResult result = results.get(i);
            if (result.success()) {
                event.success();
            } else {
                event.fail();
            }
        }

        delegate.write(chunk);
    }

    private EmailSendTask prepareSendTask(EmailOutboxEvent event) {
        return new EmailSendTask(
                event.getId(),
                event.getEmail(),
                event.getEmailSendType().getTitle(),
                event.getHtmlContent()
        );
    }

    private EmailSendResult sendEmail(EmailSendTask task) {
        try {
            boolean success = emailSender.sendEmail(task.email(), task.title(), task.htmlContent());
            return new EmailSendResult(task.outboxEventId(), success);
        } catch (RuntimeException e) {
            log.error("Email relay failed. OutboxEventId: {}, To: {}", task.outboxEventId(), task.email(), e);
            return new EmailSendResult(task.outboxEventId(), false);
        }
    }

    private record EmailSendTask(Long outboxEventId, String email, String title, String htmlContent) {
    }

    private record EmailSendResult(Long outboxEventId, boolean success) {
    }
}
