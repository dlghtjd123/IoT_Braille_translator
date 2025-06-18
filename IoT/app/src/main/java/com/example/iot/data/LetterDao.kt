package com.example.iot.data

import androidx.room.*

@Dao
interface LetterDao {
    @Query("SELECT * FROM letter_table WHERE level = :level")
    suspend fun getLettersByLevel(level: Int): List<LetterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(letters: List<LetterEntity>)

    @Query("SELECT * FROM letter_table WHERE text = :text LIMIT 1")
    suspend fun getLetterByText(text: String): LetterEntity?
}
