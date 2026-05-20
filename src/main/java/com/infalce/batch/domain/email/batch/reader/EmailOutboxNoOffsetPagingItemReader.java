package com.infalce.batch.domain.email.batch.reader;

import com.infalce.batch.entity.email.EmailOutboxEvent;
import com.infalce.batch.entity.email.PublishStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.springframework.batch.item.database.AbstractPagingItemReader;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.util.Assert;

import java.util.ArrayList;

public class EmailOutboxNoOffsetPagingItemReader extends AbstractPagingItemReader<EmailOutboxEvent> {

    private final EntityManagerFactory entityManagerFactory;
    private final int maxAttemptCount;

    private EntityManager entityManager;
    private long lastSeenId;

    public EmailOutboxNoOffsetPagingItemReader(
            EntityManagerFactory entityManagerFactory,
            int pageSize,
            int maxAttemptCount,
            int maxItemCount
    ) {
        Assert.notNull(entityManagerFactory, "entityManagerFactory must not be null");
        Assert.isTrue(pageSize > 0, "pageSize must be greater than zero");
        Assert.isTrue(maxAttemptCount > 0, "maxAttemptCount must be greater than zero");
        Assert.isTrue(maxItemCount > 0, "maxItemCount must be greater than zero");

        this.entityManagerFactory = entityManagerFactory;
        this.maxAttemptCount = maxAttemptCount;
        setPageSize(pageSize);
        setMaxItemCount(maxItemCount);
    }

    @Override
    protected void doOpen() throws Exception {
        super.doOpen();
        entityManager = entityManagerFactory.createEntityManager();
        if (entityManager == null) {
            throw new DataAccessResourceFailureException("Unable to obtain an EntityManager");
        }
        lastSeenId = 0L;
    }

    @Override
    protected void doReadPage() {
        if (results == null) {
            results = new ArrayList<>();
        } else {
            results.clear();
        }

        TypedQuery<EmailOutboxEvent> query = entityManager.createQuery("""
                select event
                from EmailOutboxEvent event
                where event.publishStatus in (:pendingStatus, :failedStatus)
                  and event.attemptCount < :maxAttemptCount
                  and event.id > :lastSeenId
                order by event.id asc
                """, EmailOutboxEvent.class);
        query.setParameter("pendingStatus", PublishStatus.PENDING);
        query.setParameter("failedStatus", PublishStatus.FAILED);
        query.setParameter("maxAttemptCount", maxAttemptCount);
        query.setParameter("lastSeenId", lastSeenId);
        query.setMaxResults(getPageSize());

        results.addAll(query.getResultList());
        if (!results.isEmpty()) {
            lastSeenId = results.get(results.size() - 1).getId();
        }
        entityManager.clear();
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        if (entityManager != null) {
            entityManager.close();
        }
    }
}
