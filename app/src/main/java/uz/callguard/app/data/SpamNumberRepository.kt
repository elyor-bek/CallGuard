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
        val normalized = NumberFormatRules.check(rawNumber).normalized
        val hash = HashUtils.sha256(normalized)
        return dao.isSpamByHash(hash)
    }

    // --- CRUD для UI / UserReportModule ---

    suspend fun addNumber(rawNumber: String, source: String = "manual") {
        val normalized = NumberFormatRules.check(rawNumber).normalized
        val hash = HashUtils.sha256(normalized)
        database.withTransaction {
            val existing = dao.findByHash(hash)
            if (existing != null) {
                dao.update(existing.copy(reportCount = existing.reportCount + 1, phoneNumber = normalized))
            } else {
                dao.insert(SpamNumberEntity(numberHash = hash, source = source, phoneNumber = normalized))
            }
        }
    }

    /** Both writes commit together; a failed insert leaves the history entry intact. */
    suspend fun reportCallAsSpam(call: CallHistoryEntity) {
        database.withTransaction {
            addNumber(call.number, source = "user_report")
            database.callHistoryDao().deleteById(call.id)
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

    suspend fun count(): Int = dao.count()

    suspend fun clearAll() = dao.clearAll()
}
