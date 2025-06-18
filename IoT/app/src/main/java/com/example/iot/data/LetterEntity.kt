package com.example.iot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "letter_table")
data class LetterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val level: Int,               // 1: 자음/모음, 2: 글자, 3: 단어
    val text: String,             // 예: "ㄱ", "가", "사랑"
    val braillePattern: String    // 점자 패턴 예: "100000"
)
