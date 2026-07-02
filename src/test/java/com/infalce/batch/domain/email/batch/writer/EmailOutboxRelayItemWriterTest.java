package com.infalce.batch.domain.email.batch.writer;

import com.infalce.batch.domain.email.service.EmailSender;
import com.infalce.batch.entity.email.EmailOutboxEvent;
import com.infalce.batch.entity.email.EmailSendType;
import com.infalce.batch.entity.email.PublishStatus;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class EmailOutboxRelayItemWriterTest {

    @Test
    void writeSubmitsEmailSendTasksBeforeJoiningResults() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        ConcurrentEmailSender emailSender = new ConcurrentEmailSender(2);
        CapturingItemWriter delegate = new CapturingItemWriter();
        EmailOutboxRelayItemWriter writer = new EmailOutboxRelayItemWriter(emailSender, delegate, executor);

        EmailOutboxEvent first = EmailOutboxEvent.of("first@example.com", EmailSendType.EXAMPLE, "<p>first</p>");
        EmailOutboxEvent second = EmailOutboxEvent.of("second@example.com", EmailSendType.EXAMPLE, "<p>second</p>");

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> writer.write(Chunk.of(first, second)));

        executor.shutdownNow();

        assertThat(emailSender.maxActiveCount()).isEqualTo(2);
        assertThat(delegate.writtenItems()).containsExactly(first, second);
        assertThat(first.getPublishStatus()).isEqualTo(PublishStatus.SUCCEEDED);
        assertThat(second.getPublishStatus()).isEqualTo(PublishStatus.SUCCEEDED);
    }

    private static class ConcurrentEmailSender implements EmailSender {

        private final CountDownLatch allTasksStarted;
        private final AtomicInteger activeCount = new AtomicInteger();
        private final AtomicInteger maxActiveCount = new AtomicInteger();

        private ConcurrentEmailSender(int taskCount) {
            this.allTasksStarted = new CountDownLatch(taskCount);
        }

        @Override
        public boolean sendEmail(String to, String title, String htmlContent) {
            int currentActiveCount = activeCount.incrementAndGet();
            maxActiveCount.accumulateAndGet(currentActiveCount, Math::max);
            allTasksStarted.countDown();
            try {
                allTasksStarted.await(1, TimeUnit.SECONDS);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                activeCount.decrementAndGet();
            }
        }

        private int maxActiveCount() {
            return maxActiveCount.get();
        }
    }

    private static class CapturingItemWriter implements ItemWriter<EmailOutboxEvent> {

        private List<EmailOutboxEvent> writtenItems = List.of();

        @Override
        public void write(Chunk<? extends EmailOutboxEvent> chunk) {
            writtenItems = chunk.getItems().stream()
                    .map(EmailOutboxEvent.class::cast)
                    .toList();
        }

        private List<EmailOutboxEvent> writtenItems() {
            return writtenItems;
        }
    }
}
