package uz.callguard.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import uz.callguard.app.heuristic.NumberFormatRules

/**
 * Единая точка входа в LocalSpamDB.
 */
class SpamNumberRepository(context: Context) {

    private val dao = CallGuardDatabase.getInstance(context).spamNumberDao()

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
        val existing = dao.findByHash(hash)
        if (existing != null) {
            dao.incrementReportCount(hash)
        } else {
            dao.insert(SpamNumberEntity(numberHash = hash, source = source))
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
