package com.vipinkumar.orderflow.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    List<AuditEvent> findByAggregateIdOrderByOccurredAtAsc(String aggregateId);

    List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
