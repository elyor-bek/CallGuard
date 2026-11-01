package uz.callguard.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

@Database(entities = [SpamNumberEntity::class, CallHistoryEntity::class], version = 2, exportSchema = false)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun callHistoryDao(): CallHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CallGuardDatabase? = null

        fun getInstance(context: Context): CallGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallGuardDatabase::class.java,
                    "callguard_local_spam_db"
                )
                .fallbackToDestructiveMigration()
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
