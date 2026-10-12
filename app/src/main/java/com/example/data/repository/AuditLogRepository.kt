package com.example.data.repository

import com.example.data.local.dao.AuditLogDao
import com.example.data.local.entity.AuditLogEntity
import com.example.service.audit.IAuditLoggerService
import kotlinx.coroutines.flow.Flow

class AuditLogRepository(
    private val dao: AuditLogDao,
    private val auditLoggerService: IAuditLoggerService
) : IAuditLoggerService by auditLoggerService {

    override val recentLogs: Flow<List<AuditLogEntity>> = dao.getRecentLogs()

    fun getLogsByCategory(category: String): Flow<List<AuditLogEntity>> = dao.getLogsByCategory(category)
    fun getLogsBySeverity(severity: String): Flow<List<AuditLogEntity>> = dao.getLogsBySeverity(severity)

    suspend fun log(action: String, detail: String, severity: String = "INFO", category: String = "SYSTEM") {
        auditLoggerService.logSecurityEvent(
            action = action,
            category = category,
            details = detail,
            severity = severity
        )
    }

    suspend fun clearLogs() {
        auditLoggerService.clearAuditLogs()
    }
}
