package com.programmersbox.timetexter

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.*

@Entity(tableName = "TextInfo")
data class TextInfo(
    @ColumnInfo(name = "id")
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "type")
    val type: TimeType,
    @ColumnInfo(name = "time")
    val time: Long,
    @ColumnInfo(name = "timeInfo")
    val timeInfo: TextTime,
    @ColumnInfo(name = "isActive")
    var isActive: Boolean,
    @ColumnInfo(name = "textTo")
    val numbers: List<String>
)

enum class TimeType {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

data class TextTime(
    val type: TimeType,
    val date: LocalDate,
    val time: String,
    val amPm: String?,
    val weekDay: Int?
)

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun newItem(item: TextInfo)

    @Delete
    suspend fun removeItem(item: TextInfo)

    @Query("select * from TextInfo")
    fun getAll(): Flow<List<TextInfo>>

    @Query("select * from TextInfo where id == :id")
    suspend fun getItem(id: String): TextInfo?

    @Update
    suspend fun updateItem(item: TextInfo): Int

}

@Database(entities = [TextInfo::class], version = 1)
@TypeConverters(Converters::class)
abstract class ItemDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {

        @Volatile
        private var INSTANCE: ItemDatabase? = null

        fun getInstance(context: Context): ItemDatabase =
            INSTANCE ?: synchronized(this) { INSTANCE ?: buildDatabase(context).also { INSTANCE = it } }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext, ItemDatabase::class.java, "textInfo.db")
                .build()
    }

}

object Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        return value?.fromJson<List<String>>().orEmpty()
    }

    @TypeConverter
    fun fromArrayList(list: List<String>?): String {
        return list.toJson()
    }

    @TypeConverter
    fun fromTimeType(value: TimeType): String {
        return value.name
    }

    @TypeConverter
    fun fromStringToTime(value: String): TimeType {
        return TimeType.valueOf(value)
    }

    @TypeConverter
    fun fromTextTime(value: TextTime): String {
        return value.toJson()
    }

    @TypeConverter
    fun toTextTime(value: String): TextTime {
        return value.fromJson<TextTime>()!!
    }
}