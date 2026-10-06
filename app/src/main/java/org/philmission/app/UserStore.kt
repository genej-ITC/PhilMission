package org.philmission.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settings by preferencesDataStore("settings")

@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val id: String)

@Entity(tableName = "personal_contacts")
data class PersonalContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val memo: String,
)

/** 기본 연락처를 사용자가 고친 내용. 행이 없으면 앱에 내장된 기본값을 쓴다. */
@Entity(tableName = "base_contact_overrides")
data class BaseContactOverride(@PrimaryKey val id: String, val name: String, val phone: String, val memo: String)

@Dao interface BaseContactDao {
    @Query("SELECT * FROM base_contact_overrides") fun observe(): Flow<List<BaseContactOverride>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: BaseContactOverride)
    @Query("DELETE FROM base_contact_overrides WHERE id = :id") suspend fun reset(id: String)
}

@Entity(tableName = "user_songs")
data class UserSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val youtube: String,
    val fileBase: String,
    val createdAt: Long,
    /** "song"(찬양) 또는 "message"(말씀) */
    @ColumnInfo(defaultValue = "'song'") val kind: String = "song",
)

@Dao interface UserSongDao {
    @Query("SELECT * FROM user_songs ORDER BY createdAt") fun observe(): Flow<List<UserSong>>
    @Insert suspend fun add(item: UserSong)
    @Delete suspend fun delete(item: UserSong)
}

@Dao interface FavoriteDao {
    @Query("SELECT id FROM favorites") fun observe(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(item: Favorite)
    @Query("DELETE FROM favorites WHERE id = :id") suspend fun remove(id: String)
}

@Dao interface ContactDao {
    @Query("SELECT * FROM personal_contacts ORDER BY name") fun observe(): Flow<List<PersonalContact>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: PersonalContact)
    @Delete suspend fun delete(item: PersonalContact)
}

@Database(entities = [Favorite::class, PersonalContact::class, UserSong::class, BaseContactOverride::class], version = 5, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun contacts(): ContactDao
    abstract fun userSongs(): UserSongDao
    abstract fun baseContacts(): BaseContactDao
}

// 기존 사용자 데이터를 지우지 않고 개인 연락처 테이블만 추가한다.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `memo` TEXT NOT NULL)")
    }
}

// 기존 데이터를 지우지 않고 사용자 찬양 테이블만 추가한다.
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_songs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `youtube` TEXT NOT NULL, `fileBase` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
    }
}

// 사용자 자료에 종류(찬양/말씀) 열을 추가한다. 기존 항목은 모두 찬양이다.
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_songs` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'song'")
    }
}

// 기본 연락처 수정본 테이블을 추가한다.
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `base_contact_overrides` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `memo` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

/** 개인 연락처 입력 검증. 이름은 필수, 전화번호는 숫자·공백·+·-·괄호만 허용한다. */
fun validateContact(name: String, phone: String, phoneRequired: Boolean = true): String? = when {
    name.isBlank() -> "이름을 입력해 주세요."
    phone.isBlank() -> if (phoneRequired) "전화번호를 입력해 주세요." else null
    !phone.all { it.isDigit() || it in "+-() " } || phone.count { it.isDigit() } < 3 -> "전화번호 형식을 확인해 주세요."
    else -> null
}

class UserStore(private val context: Context) {
    private val db = Room.databaseBuilder(context, UserDatabase::class.java, "philmission.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
    val favorites = db.favorites().observe()
    val personalContacts = db.contacts().observe()
    val userSongs = db.userSongs().observe()
    val baseContactOverrides = db.baseContacts().observe()

    private val langKey = stringPreferencesKey("language")
    private val sizeKey = intPreferencesKey("font_step")
    private val awakeKey = booleanPreferencesKey("keep_awake")
    val language = context.settings.data.map { it[langKey] ?: "tl" }
    /** 0=보통 1=크게 2=아주 크게 */
    val fontStep = context.settings.data.map { it[sizeKey] ?: 1 }
    val keepAwake = context.settings.data.map { it[awakeKey] ?: true }

    suspend fun language(value: String) { context.settings.edit { it[langKey] = value } }
    suspend fun fontStep(value: Int) { context.settings.edit { it[sizeKey] = value.coerceIn(0, 2) } }
    suspend fun keepAwake(value: Boolean) { context.settings.edit { it[awakeKey] = value } }
    suspend fun favorite(id: String, enabled: Boolean) {
        if (enabled) db.favorites().add(Favorite(id)) else db.favorites().remove(id)
    }
    suspend fun saveContact(item: PersonalContact) = db.contacts().save(item)
    suspend fun deleteContact(item: PersonalContact) = db.contacts().delete(item)
    suspend fun saveBaseContact(item: BaseContactOverride) = db.baseContacts().save(item)
    suspend fun resetBaseContact(id: String) = db.baseContacts().reset(id)
    suspend fun saveUserSong(item: UserSong) = db.userSongs().add(item)
    suspend fun deleteUserSong(item: UserSong) {
        db.userSongs().delete(item)
        withContext(Dispatchers.IO) { deleteUserDoc(context, item.fileBase) }
    }
}
