package com.infalce.batch.domain.email.repository;

import com.infalce.batch.entity.email.EmailOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxEventRepository extends JpaRepository<EmailOutboxEvent, Long> {
}
