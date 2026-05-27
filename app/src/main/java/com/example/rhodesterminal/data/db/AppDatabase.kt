package com.example.rhodesterminal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.rhodesterminal.data.db.dao.ChatMessageDao
import com.example.rhodesterminal.data.db.dao.ChatSessionDao
import com.example.rhodesterminal.data.db.dao.DiaryDao
import com.example.rhodesterminal.data.db.dao.DispatchDao
import com.example.rhodesterminal.data.db.dao.MahjongSaveDao
import com.example.rhodesterminal.data.db.dao.MemoryDao
import com.example.rhodesterminal.data.db.dao.MomentDao
import com.example.rhodesterminal.data.db.dao.OperatorDao
import com.example.rhodesterminal.data.db.dao.RelationshipDao
import com.example.rhodesterminal.data.db.entity.ChatMessageEntity
import com.example.rhodesterminal.data.db.entity.ChatSessionEntity
import com.example.rhodesterminal.data.db.entity.DiaryEntity
import com.example.rhodesterminal.data.db.entity.DispatchRecordEntity
import com.example.rhodesterminal.data.db.entity.MahjongSaveEntity
import com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity
import com.example.rhodesterminal.data.db.entity.MemoryEntity
import com.example.rhodesterminal.data.db.entity.MomentCommentEntity
import com.example.rhodesterminal.data.db.entity.MomentEntity
import com.example.rhodesterminal.data.db.entity.MomentLikeEntity
import com.example.rhodesterminal.data.db.entity.OperatorEntity
import com.example.rhodesterminal.data.db.entity.RelationshipEntity

@Database(
    entities = [
        OperatorEntity::class, ChatSessionEntity::class, ChatMessageEntity::class,
        MemoryEntity::class, MemoryAnchorEntity::class,
        RelationshipEntity::class,
        MomentEntity::class, MomentCommentEntity::class, MomentLikeEntity::class,
        DiaryEntity::class,
        DispatchRecordEntity::class,
        MahjongSaveEntity::class
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun operatorDao(): OperatorDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun momentDao(): MomentDao
    abstract fun diaryDao(): DiaryDao
    abstract fun dispatchDao(): DispatchDao
    abstract fun mahjongSaveDao(): MahjongSaveDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN mutedMembers TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE operators ADD COLUMN lmb INTEGER NOT NULL DEFAULT 10000")
                db.execSQL("ALTER TABLE operators ADD COLUMN attack REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE operators ADD COLUMN defense REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE operators ADD COLUMN meldPref TEXT NOT NULL DEFAULT 'medium'")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS mahjong_saves (id TEXT NOT NULL PRIMARY KEY, saveJson TEXT NOT NULL, ruleType TEXT NOT NULL DEFAULT '', savedAt INTEGER NOT NULL DEFAULT 0)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rhodes_terminal.db"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
