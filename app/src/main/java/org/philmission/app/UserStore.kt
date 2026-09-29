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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

@Database(entities = [Favorite::class, PersonalContact::class], version = 2, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun contacts(): ContactDao
}

// 기존 사용자 데이터를 지우지 않고 개인 연락처 테이블만 추가한다.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `memo` TEXT NOT NULL)")
    }
}

/** 개인 연락처 입력 검증. 이름은 필수, 전화번호는 숫자·공백·+·-·괄호만 허용한다. */
fun validateContact(name: String, phone: String): String? = when {
    name.isBlank() -> "이름을 입력해 주세요."
    phone.isBlank() -> "전화번호를 입력해 주세요."
    !phone.all { it.isDigit() || it in "+-() " } || phone.count { it.isDigit() } < 3 -> "전화번호 형식을 확인해 주세요."
    else -> null
}

class UserStore(private val context: Context) {
    private val db = Room.databaseBuilder(context, UserDatabase::class.java, "philmission.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val favorites = db.favorites().observe()
    val personalContacts = db.contacts().observe()

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
}
