package uz.callguard.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

@Database(entities = [SpamNumberEntity::class, CallHistoryEntity::class, NumberPatternEntity::class, PendingSpamReport::class], version = 5, exportSchema = false)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun numberPatternDao(): NumberPatternDao
    abstract fun pendingSpamReportDao(): PendingSpamReportDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spam_numbers ADD COLUMN phoneNumber TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS number_patterns (pattern TEXT NOT NULL, addedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(pattern))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spam_numbers ADD COLUMN spamScore INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE spam_numbers ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE spam_numbers ADD COLUMN isGlobal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE spam_numbers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE spam_numbers SET updatedAt = addedAtEpochMs")
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_spam_reports (callId INTEGER NOT NULL, numberHash TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(callId))")
            }
        }

        @Volatile
        private var INSTANCE: CallGuardDatabase? = null

        fun getInstance(context: Context): CallGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallGuardDatabase::class.java,
                    "callguard_local_spam_db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(true)
                .addCallback(DatabaseCallback(context.applicationContext))
                .build().also { INSTANCE = it }
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                populateInitialSpamBase(context)
            }
        }

        private suspend fun populateInitialSpamBase(context: Context) {
            try {
                val jsonString = context.assets.open("spam_base.json")
                    .bufferedReader()
                    .use { it.readText() }

                val jsonArray = JSONArray(jsonString)
                val entities = mutableListOf<SpamNumberEntity>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val hash = item.optString("numberHash")
                    val source = item.optString("source", "assets")
                    if (hash.isNotBlank()) {
                        entities.add(
                            SpamNumberEntity(
                                numberHash = hash,
                                source = source
                            )
                        )
                    }
                }

                if (entities.isNotEmpty()) {
                    val dao = getInstance(context).spamNumberDao()
                    dao.insertAll(entities)
                    Log.d("CallGuardDatabase", "Инициализировано ${entities.size} стартовых хэшей из assets/spam_base.json")
                }
            } catch (e: Exception) {
                Log.e("CallGuardDatabase", "Ошибка при чтении assets/spam_base.json", e)
            }
        }
    }
}
