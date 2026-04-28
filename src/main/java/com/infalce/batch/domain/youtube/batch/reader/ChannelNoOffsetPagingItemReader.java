package com.infalce.batch.domain.youtube.batch.reader;

import com.infalce.batch.entity.channel.Channel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.springframework.batch.item.database.AbstractPagingItemReader;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.util.Assert;

import java.util.ArrayList;

public class ChannelNoOffsetPagingItemReader extends AbstractPagingItemReader<Channel> {

    private final EntityManagerFactory entityManagerFactory;
    private final long minId;
    private final long maxId;

    private EntityManager entityManager;
    private long lastSeenId;

    public ChannelNoOffsetPagingItemReader(
            EntityManagerFactory entityManagerFactory,
            int pageSize,
            long minId,
            long maxId
    ) {
        Assert.notNull(entityManagerFactory, "entityManagerFactory must not be null");
        Assert.isTrue(pageSize > 0, "pageSize must be greater than zero");

        this.entityManagerFactory = entityManagerFactory;
        this.minId = minId;
        this.maxId = maxId;
        this.lastSeenId = initialLastSeenId(minId);
        setPageSize(pageSize);
    }

    @Override
    protected void doOpen() throws Exception {
        super.doOpen();
        entityManager = entityManagerFactory.createEntityManager();
        if (entityManager == null) {
            throw new DataAccessResourceFailureException("Unable to obtain an EntityManager");
        }
        lastSeenId = initialLastSeenId(minId);
    }

    @Override
    protected void doReadPage() {
        if (results == null) {
            results = new ArrayList<>();
        } else {
            results.clear();
        }

        if (maxId < minId || lastSeenId >= maxId) {
            return;
        }

        TypedQuery<Channel> query = entityManager.createQuery("""
                select c
                from Channel c
                where c.youtubeChannelId is not null
                  and c.id > :lastSeenId
                  and c.id <= :maxId
                order by c.id asc
                """, Channel.class);
        query.setParameter("lastSeenId", lastSeenId);
        query.setParameter("maxId", maxId);
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

    private long initialLastSeenId(long minId) {
        return minId <= 0 ? 0L : minId - 1;
    }
}
