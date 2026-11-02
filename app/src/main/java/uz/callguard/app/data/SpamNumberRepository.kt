package uz.callguard.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import uz.callguard.app.heuristic.NumberFormatRules

/**
 * Единая точка входа в LocalSpamDB.
 */
class SpamNumberRepository(context: Context) {

    private val database = CallGuardDatabase.getInstance(context)
    private val dao = database.spamNumberDao()

    // --- Второй слой Architecture: используется после HeuristicFilter при verdict = UNKNOWN ---
    suspend fun isKnownSpam(rawNumber: String): Boolean {
        return SpamPolicy.classify(findNumber(rawNumber)) != SpamPolicy.Action.NONE
    }

    suspend fun findNumber(rawNumber: String): SpamNumberEntity? {
        val normalized = NumberFormatRules.check(rawNumber).normalized
        return dao.findByHash(HashUtils.sha256(normalized))
    }

    suspend fun queueComplaint(callId: Long) {
        database.withTransaction {
            val call = requireNotNull(database.callHistoryDao().findById(callId))
            val number = NumberFormatRules.check(call.number).normalized
            require(number.matches(Regex("\\+?[0-9]{3,15}")))
            database.pendingSpamReportDao().insert(PendingSpamReport(call.id, HashUtils.sha256(number)))
        }
    }

    // --- CRUD для UI / UserReportModule ---

    suspend fun addNumber(rawNumber: String, source: String = "manual") {
        val normalized = NumberFormatRules.check(rawNumber).normalized
        val hash = HashUtils.sha256(normalized)
        database.withTransaction {
            val existing = dao.findByHash(hash)
            if (existing != null) {
                dao.update(existing.copy(reportCount = existing.reportCount + 1, phoneNumber = normalized, isGlobal = false, spamScore = 100, source = source, updatedAt = System.currentTimeMillis()))
            } else {
                dao.insert(SpamNumberEntity(numberHash = hash, source = source, phoneNumber = normalized))
            }
        }
    }

    /** Both writes commit together; a failed insert leaves the history entry intact. */
    suspend fun reportCallAsSpam(call: CallHistoryEntity) {
        reportCallsAsSpam(listOf(call))
    }

    suspend fun reportCallsAsSpam(calls: List<CallHistoryEntity>) {
        if (calls.isEmpty()) return
        database.withTransaction {
            addNumber(calls.maxBy { it.timestampMs }.number, source = "user_report")
            database.callHistoryDao().deleteEntries(calls.map { it.id })
        }
    }

    suspend fun removeNumber(rawNumber: String) {
        val normalized = NumberFormatRules.check(rawNumber).normalized
        val hash = HashUtils.sha256(normalized)
        dao.deleteByHash(hash)
    }

    suspend fun removeNumberByHash(hash: String) {
        dao.deleteByHash(hash)
    }

    fun observeAll(): Flow<List<SpamNumberEntity>> = dao.observeAll()

    suspend fun exportNumbers(): List<SpamNumberEntity> = dao.getAll()

    suspend fun importNumbers(numbers: List<SpamNumberEntity>): Int = database.withTransaction {
        dao.insertAll(numbers).count { it != -1L }
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun count(): Int = dao.count()

    suspend fun clearAll() = dao.clearAll()
}
