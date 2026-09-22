package com.tenantrag.backend.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

@Service
public class TenantTransactionService {

    private final TransactionTemplate transactionTemplate;
    private final TenantDatabaseService tenantDatabaseService;

    public TenantTransactionService(
            TransactionTemplate transactionTemplate,
            TenantDatabaseService tenantDatabaseService) {

        this.transactionTemplate = transactionTemplate;
        this.tenantDatabaseService = tenantDatabaseService;
    }

    public <T> T execute(UUID tenantId, Supplier<T> action) {

        return transactionTemplate.execute(status -> {

            tenantDatabaseService.setTenant(tenantId);

            return action.get();
        });
    }
}