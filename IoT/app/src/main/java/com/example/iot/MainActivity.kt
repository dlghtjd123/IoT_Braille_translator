package com.example.iot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.iot.data.LetterDao
import com.example.iot.data.LetterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var db: AppDatabase
    private val client = OkHttpClient()
    private val nodeMcuUrl = "http://192.168.4.1/braille" // NodeMCU IP로 변경 필요
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)
        insertSampleDataIfNeeded()

        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.btnLevel1).setOnClickListener {
            sendBrailleByLevel(1)
        }
        findViewById<Button>(R.id.btnLevel2).setOnClickListener {
            sendBrailleByLevel(2)
        }
        findViewById<Button>(R.id.btnLevel3).setOnClickListener {
            sendBrailleByLevel(3)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "TTS 언어 미지원", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "TTS 초기화 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertSampleDataIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.letterDao()

            if (dao.getLettersByLevel(1).isEmpty()) {
                insertLevel1Data(dao)
            }
            if (dao.getLettersByLevel(2).isEmpty()) {
                insertLevel2Data(dao)
            }
            if (dao.getLettersByLevel(3).isEmpty()) {
                insertLevel3Data(dao)
            }
        }
    }

    private fun sendBrailleByLevel(level: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val letters = db.letterDao().getLettersByLevel(level)
            if (letters.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "해당 단계에 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val selected = letters.random()
            sendToNodeMCU(selected.text)
        }
    }

    private fun sendToNodeMCU(text: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = """{"text":"$text"}"""
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = json.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(nodeMcuUrl)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "$text 전송 성공", Toast.LENGTH_SHORT).show()

                        // 10초 후 TTS 발음
                        Handler(Looper.getMainLooper()).postDelayed({
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                        }, 10_000)

                    } else {
                        Toast.makeText(this@MainActivity, "전송 실패: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "HTTP 예외 발생", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    private suspend fun insertLevel1Data(dao: LetterDao) {
        dao.insertAll(
            listOf(
                LetterEntity(level = 1, text = "ㄱ", braillePattern = "100000"),
                LetterEntity(level = 1, text = "ㄴ", braillePattern = "101000"),
                LetterEntity(level = 1, text = "ㄷ", braillePattern = "110000"),
                LetterEntity(level = 1, text = "ㄹ", braillePattern = "100100"),
                LetterEntity(level = 1, text = "ㅁ", braillePattern = "111000"),
                LetterEntity(level = 1, text = "ㅂ", braillePattern = "101100"),
                LetterEntity(level = 1, text = "ㅅ", braillePattern = "010100"),
                LetterEntity(level = 1, text = "ㅈ", braillePattern = "100110"),
                LetterEntity(level = 1, text = "ㅊ", braillePattern = "101110"),
                LetterEntity(level = 1, text = "ㅋ", braillePattern = "110100"),
                LetterEntity(level = 1, text = "ㅌ", braillePattern = "111100"),
                LetterEntity(level = 1, text = "ㅍ", braillePattern = "110110"),
                LetterEntity(level = 1, text = "ㅎ", braillePattern = "011010"),
                LetterEntity(level = 1, text = "ㅏ", braillePattern = "101010"),
                LetterEntity(level = 1, text = "ㅑ", braillePattern = "101011"),
                LetterEntity(level = 1, text = "ㅓ", braillePattern = "101100"),
                LetterEntity(level = 1, text = "ㅕ", braillePattern = "101101"),
                LetterEntity(level = 1, text = "ㅗ", braillePattern = "101110"),
                LetterEntity(level = 1, text = "ㅛ", braillePattern = "101111"),
                LetterEntity(level = 1, text = "ㅜ", braillePattern = "110100"),
                LetterEntity(level = 1, text = "ㅠ", braillePattern = "110101"),
                LetterEntity(level = 1, text = "ㅡ", braillePattern = "110110"),
                LetterEntity(level = 1, text = "ㅣ", braillePattern = "010010")
            )
        )
    }

    private suspend fun insertLevel2Data(dao: LetterDao) {
        dao.insertAll(
            listOf(
                LetterEntity(level = 2, text = "가", braillePattern = "100000101010"),
                LetterEntity(level = 2, text = "갸", braillePattern = "100000101011"),
                LetterEntity(level = 2, text = "거", braillePattern = "100000101100"),
                LetterEntity(level = 2, text = "겨", braillePattern = "100000101101"),
                LetterEntity(level = 2, text = "고", braillePattern = "100000101110"),
                LetterEntity(level = 2, text = "교", braillePattern = "100000101111"),
                LetterEntity(level = 2, text = "구", braillePattern = "100000110100"),
                LetterEntity(level = 2, text = "규", braillePattern = "100000110101"),
                LetterEntity(level = 2, text = "그", braillePattern = "100000110110"),
                LetterEntity(level = 2, text = "기", braillePattern = "100000010010"),
                LetterEntity(level = 2, text = "나", braillePattern = "101000101010"),
                LetterEntity(level = 2, text = "냐", braillePattern = "101000101011"),
                LetterEntity(level = 2, text = "너", braillePattern = "101000101100"),
                LetterEntity(level = 2, text = "녀", braillePattern = "101000101101"),
                LetterEntity(level = 2, text = "노", braillePattern = "101000101110"),
                LetterEntity(level = 2, text = "뇨", braillePattern = "101000101111"),
                LetterEntity(level = 2, text = "누", braillePattern = "101000110100"),
                LetterEntity(level = 2, text = "뉴", braillePattern = "101000110101"),
                LetterEntity(level = 2, text = "느", braillePattern = "101000110110"),
                LetterEntity(level = 2, text = "니", braillePattern = "101000010010"),
                LetterEntity(level = 2, text = "다", braillePattern = "110000101010"),
                LetterEntity(level = 2, text = "댜", braillePattern = "110000101011"),
                LetterEntity(level = 2, text = "더", braillePattern = "110000101100"),
                LetterEntity(level = 2, text = "뎌", braillePattern = "110000101101"),
                LetterEntity(level = 2, text = "도", braillePattern = "110000101110"),
                LetterEntity(level = 2, text = "됴", braillePattern = "110000101111"),
                LetterEntity(level = 2, text = "두", braillePattern = "110000110100"),
                LetterEntity(level = 2, text = "듀", braillePattern = "110000110101"),
                LetterEntity(level = 2, text = "드", braillePattern = "110000110110"),
                LetterEntity(level = 2, text = "디", braillePattern = "110000010010"),
                LetterEntity(level = 2, text = "라", braillePattern = "100100101010"),
                LetterEntity(level = 2, text = "랴", braillePattern = "100100101011"),
                LetterEntity(level = 2, text = "러", braillePattern = "100100101100"),
                LetterEntity(level = 2, text = "려", braillePattern = "100100101101"),
                LetterEntity(level = 2, text = "로", braillePattern = "100100101110"),
                LetterEntity(level = 2, text = "료", braillePattern = "100100101111"),
                LetterEntity(level = 2, text = "루", braillePattern = "100100110100"),
                LetterEntity(level = 2, text = "류", braillePattern = "100100110101"),
                LetterEntity(level = 2, text = "르", braillePattern = "100100110110"),
                LetterEntity(level = 2, text = "리", braillePattern = "100100010010"),
                LetterEntity(level = 2, text = "마", braillePattern = "111000101010"),
                LetterEntity(level = 2, text = "먀", braillePattern = "111000101011"),
                LetterEntity(level = 2, text = "머", braillePattern = "111000101100"),
                LetterEntity(level = 2, text = "며", braillePattern = "111000101101"),
                LetterEntity(level = 2, text = "모", braillePattern = "111000101110"),
                LetterEntity(level = 2, text = "묘", braillePattern = "111000101111"),
                LetterEntity(level = 2, text = "무", braillePattern = "111000110100"),
                LetterEntity(level = 2, text = "뮤", braillePattern = "111000110101"),
                LetterEntity(level = 2, text = "므", braillePattern = "111000110110"),
                LetterEntity(level = 2, text = "미", braillePattern = "111000010010"),
                LetterEntity(level = 2, text = "바", braillePattern = "101100101010"),
                LetterEntity(level = 2, text = "뱌", braillePattern = "101100101011"),
                LetterEntity(level = 2, text = "버", braillePattern = "101100101100"),
                LetterEntity(level = 2, text = "벼", braillePattern = "101100101101"),
                LetterEntity(level = 2, text = "보", braillePattern = "101100101110"),
                LetterEntity(level = 2, text = "뵤", braillePattern = "101100101111"),
                LetterEntity(level = 2, text = "부", braillePattern = "101100110100"),
                LetterEntity(level = 2, text = "뷰", braillePattern = "101100110101"),
                LetterEntity(level = 2, text = "브", braillePattern = "101100110110"),
                LetterEntity(level = 2, text = "비", braillePattern = "101100010010"),
                LetterEntity(level = 2, text = "사", braillePattern = "010100101010"),
                LetterEntity(level = 2, text = "샤", braillePattern = "010100101011"),
                LetterEntity(level = 2, text = "서", braillePattern = "010100101100"),
                LetterEntity(level = 2, text = "셔", braillePattern = "010100101101"),
                LetterEntity(level = 2, text = "소", braillePattern = "010100101110"),
                LetterEntity(level = 2, text = "쇼", braillePattern = "010100101111"),
                LetterEntity(level = 2, text = "수", braillePattern = "010100110100"),
                LetterEntity(level = 2, text = "슈", braillePattern = "010100110101"),
                LetterEntity(level = 2, text = "스", braillePattern = "010100110110"),
                LetterEntity(level = 2, text = "시", braillePattern = "010100010010"),
                LetterEntity(level = 2, text = "아", braillePattern = "011100101010"),
                LetterEntity(level = 2, text = "야", braillePattern = "011100101011"),
                LetterEntity(level = 2, text = "어", braillePattern = "011100101100"),
                LetterEntity(level = 2, text = "여", braillePattern = "011100101101"),
                LetterEntity(level = 2, text = "오", braillePattern = "011100101110"),
                LetterEntity(level = 2, text = "요", braillePattern = "011100101111"),
                LetterEntity(level = 2, text = "우", braillePattern = "011100110100"),
                LetterEntity(level = 2, text = "유", braillePattern = "011100110101"),
                LetterEntity(level = 2, text = "으", braillePattern = "011100110110"),
                LetterEntity(level = 2, text = "이", braillePattern = "011100010010"),
                LetterEntity(level = 2, text = "자", braillePattern = "100110101010"),
                LetterEntity(level = 2, text = "쟈", braillePattern = "100110101011"),
                LetterEntity(level = 2, text = "저", braillePattern = "100110101100"),
                LetterEntity(level = 2, text = "져", braillePattern = "100110101101"),
                LetterEntity(level = 2, text = "조", braillePattern = "100110101110"),
                LetterEntity(level = 2, text = "죠", braillePattern = "100110101111"),
                LetterEntity(level = 2, text = "주", braillePattern = "100110110100"),
                LetterEntity(level = 2, text = "쥬", braillePattern = "100110110101"),
                LetterEntity(level = 2, text = "즈", braillePattern = "100110110110"),
                LetterEntity(level = 2, text = "지", braillePattern = "100110010010"),
                LetterEntity(level = 2, text = "차", braillePattern = "101110101010"),
                LetterEntity(level = 2, text = "챠", braillePattern = "101110101011"),
                LetterEntity(level = 2, text = "처", braillePattern = "101110101100"),
                LetterEntity(level = 2, text = "쳐", braillePattern = "101110101101"),
                LetterEntity(level = 2, text = "초", braillePattern = "101110101110"),
                LetterEntity(level = 2, text = "쵸", braillePattern = "101110101111"),
                LetterEntity(level = 2, text = "추", braillePattern = "101110110100"),
                LetterEntity(level = 2, text = "츄", braillePattern = "101110110101"),
                LetterEntity(level = 2, text = "츠", braillePattern = "101110110110"),
                LetterEntity(level = 2, text = "치", braillePattern = "101110010010"),
                LetterEntity(level = 2, text = "카", braillePattern = "110100101010"),
                LetterEntity(level = 2, text = "캬", braillePattern = "110100101011"),
                LetterEntity(level = 2, text = "커", braillePattern = "110100101100"),
                LetterEntity(level = 2, text = "켜", braillePattern = "110100101101"),
                LetterEntity(level = 2, text = "코", braillePattern = "110100101110"),
                LetterEntity(level = 2, text = "쿄", braillePattern = "110100101111"),
                LetterEntity(level = 2, text = "쿠", braillePattern = "110100110100"),
                LetterEntity(level = 2, text = "큐", braillePattern = "110100110101"),
                LetterEntity(level = 2, text = "크", braillePattern = "110100110110"),
                LetterEntity(level = 2, text = "키", braillePattern = "110100010010"),
                LetterEntity(level = 2, text = "타", braillePattern = "111100101010"),
                LetterEntity(level = 2, text = "탸", braillePattern = "111100101011"),
                LetterEntity(level = 2, text = "터", braillePattern = "111100101100"),
                LetterEntity(level = 2, text = "텨", braillePattern = "111100101101"),
                LetterEntity(level = 2, text = "토", braillePattern = "111100101110"),
                LetterEntity(level = 2, text = "툐", braillePattern = "111100101111"),
                LetterEntity(level = 2, text = "투", braillePattern = "111100110100"),
                LetterEntity(level = 2, text = "튜", braillePattern = "111100110101"),
                LetterEntity(level = 2, text = "트", braillePattern = "111100110110"),
                LetterEntity(level = 2, text = "티", braillePattern = "111100010010"),
                LetterEntity(level = 2, text = "파", braillePattern = "110110101010"),
                LetterEntity(level = 2, text = "퍄", braillePattern = "110110101011"),
                LetterEntity(level = 2, text = "퍼", braillePattern = "110110101100"),
                LetterEntity(level = 2, text = "펴", braillePattern = "110110101101"),
                LetterEntity(level = 2, text = "포", braillePattern = "110110101110"),
                LetterEntity(level = 2, text = "표", braillePattern = "110110101111"),
                LetterEntity(level = 2, text = "푸", braillePattern = "110110110100"),
                LetterEntity(level = 2, text = "퓨", braillePattern = "110110110101"),
                LetterEntity(level = 2, text = "프", braillePattern = "110110110110"),
                LetterEntity(level = 2, text = "피", braillePattern = "110110010010"),
                LetterEntity(level = 2, text = "하", braillePattern = "011010101010"),
                LetterEntity(level = 2, text = "햐", braillePattern = "011010101011"),
                LetterEntity(level = 2, text = "허", braillePattern = "011010101100"),
                LetterEntity(level = 2, text = "혀", braillePattern = "011010101101"),
                LetterEntity(level = 2, text = "호", braillePattern = "011010101110"),
                LetterEntity(level = 2, text = "효", braillePattern = "011010101111"),
                LetterEntity(level = 2, text = "후", braillePattern = "011010110100"),
                LetterEntity(level = 2, text = "휴", braillePattern = "011010110101"),
                LetterEntity(level = 2, text = "흐", braillePattern = "011010110110"),
                LetterEntity(level = 2, text = "히", braillePattern = "011010010010")
            )
        )
    }

    private suspend fun insertLevel3Data(dao: LetterDao) {
        dao.insertAll(
            listOf(
                LetterEntity(level = 3, text = "가", braillePattern = "111001"),
                LetterEntity(level = 3, text = "나", braillePattern = "101101"),
                LetterEntity(level = 3, text = "다", braillePattern = "111101"),
                LetterEntity(level = 3, text = "마", braillePattern = "101011"),
                LetterEntity(level = 3, text = "바", braillePattern = "111011"),
                LetterEntity(level = 3, text = "사", braillePattern = "011111"),
                LetterEntity(level = 3, text = "자", braillePattern = "111111"),
                LetterEntity(level = 3, text = "카", braillePattern = "101111"),
                LetterEntity(level = 3, text = "타", braillePattern = "011101"),
                LetterEntity(level = 3, text = "파", braillePattern = "011011"),
                LetterEntity(level = 3, text = "하", braillePattern = "110111"),
                LetterEntity(level = 3, text = "것", braillePattern = "100111"),
                LetterEntity(level = 3, text = "억", braillePattern = "100001"),
                LetterEntity(level = 3, text = "언", braillePattern = "101001"),
                LetterEntity(level = 3, text = "얼", braillePattern = "110001"),
                LetterEntity(level = 3, text = "연", braillePattern = "100101"),
                LetterEntity(level = 3, text = "열", braillePattern = "111001"),
                LetterEntity(level = 3, text = "영", braillePattern = "101101"),
                LetterEntity(level = 3, text = "옥", braillePattern = "011001"),
                LetterEntity(level = 3, text = "운", braillePattern = "111100"),
                LetterEntity(level = 3, text = "움", braillePattern = "111010"),
                LetterEntity(level = 3, text = "을", braillePattern = "110101"),
                LetterEntity(level = 3, text = "은", braillePattern = "011101"),
                LetterEntity(level = 3, text = "인", braillePattern = "110011"),
                LetterEntity(level = 3, text = "그래서", braillePattern = "101110"),
                LetterEntity(level = 3, text = "그러나", braillePattern = "110110"),
                LetterEntity(level = 3, text = "그러면", braillePattern = "100110"),
                LetterEntity(level = 3, text = "그러므로", braillePattern = "111110"),
                LetterEntity(level = 3, text = "그런데", braillePattern = "111010"),
                LetterEntity(level = 3, text = "그리고", braillePattern = "110010"),
                LetterEntity(level = 3, text = "그리하여", braillePattern = "101010")
            )
        )
    }
}
