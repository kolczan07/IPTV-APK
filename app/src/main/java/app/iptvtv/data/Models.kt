package app.iptvtv.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tvgId: String?,
    val name: String,
    val logo: String?,
    val group: String,
    val number: Int?,
    val url: String,
    val favorite: Boolean = false,
    val position: Int,
)

@Entity(tableName = "programmes", indices = [Index("channelKey", "start")])
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelKey: String,   // id kanału z XMLTV, małymi literami
    val start: Long,          // epoch ms
    val stop: Long,
    val title: String,
    val description: String?,
)

/** Historia oglądania po adresie strumienia, dzięki temu przeżywa odświeżenie playlisty. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val watchedAt: Long,
)

@Dao
abstract class ChannelDao {
    @Query("SELECT * FROM channels ORDER BY position")
    abstract fun all(): Flow<List<ChannelEntity>>

    @Query("SELECT url FROM channels WHERE favorite = 1")
    abstract suspend fun favoriteUrls(): List<String>

    @Query("DELETE FROM channels")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(items: List<ChannelEntity>)

    @Query("UPDATE channels SET favorite = :fav WHERE id = :id")
    abstract suspend fun setFavorite(id: Long, fav: Boolean)

    /** Podmienia playlistę, zachowując ulubione (po adresie strumienia). */
    @Transaction
    open suspend fun replaceAll(items: List<ChannelEntity>) {
        val favs = favoriteUrls().toSet()
        clear()
        insertAll(items.map { it.copy(favorite = it.url in favs) })
    }
}

@Dao
abstract class EpgDao {
    @Query("SELECT * FROM programmes WHERE stop > :from AND start < :to ORDER BY start")
    abstract fun window(from: Long, to: Long): Flow<List<ProgrammeEntity>>

    @Query("DELETE FROM programmes")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(items: List<ProgrammeEntity>)

    @Transaction
    open suspend fun replaceAll(items: List<ProgrammeEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
abstract class HistoryDao {
    @Query("SELECT url FROM history ORDER BY watchedAt DESC LIMIT 50")
    abstract fun recentUrls(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE url NOT IN (SELECT url FROM history ORDER BY watchedAt DESC LIMIT 50)")
    abstract suspend fun trim()

    @Query("DELETE FROM history")
    abstract suspend fun clear()

    @Transaction
    open suspend fun mark(url: String, at: Long) {
        upsert(HistoryEntity(url, at))
        trim()
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history` (`url` TEXT NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
        )
    }
}

@Database(
    entities = [ChannelEntity::class, ProgrammeEntity::class, HistoryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "iptv.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
