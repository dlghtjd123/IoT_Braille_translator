package com.example.iot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var db: AppDatabase
    private val client = OkHttpClient()
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)
        insertSampleDataIfNeeded()
        tts = TextToSpeech(this, this)

        if (getSavedUrl() == null) {
            showUrlInputDialog()
        }

        findViewById<Button>(R.id.btnLevel1).setOnClickListener {
            sendBrailleByLevel(1)
        }
        findViewById<Button>(R.id.btnLevel2).setOnClickListener {
            sendBrailleByLevel(2)
        }
        findViewById<Button>(R.id.btnLevel3).setOnClickListener {
            sendBrailleByLevel(3)
        }

        findViewById<Button>(R.id.btnResetUrl).setOnClickListener {
            saveUrl(null.toString())
            showUrlInputDialog()
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

    private fun showUrlInputDialog() {
        val editText = EditText(this)
        editText.hint = "예: http://192.168.4.1/braille"

        AlertDialog.Builder(this)
            .setTitle("NodeMCU 주소 입력")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("저장") { _, _ ->
                val url = editText.text.toString()
                if (url.isNotEmpty()) {
                    saveUrl(url)
                    Toast.makeText(this, "URL 저장됨: $url", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "URL이 비어 있습니다.", Toast.LENGTH_SHORT).show()
                    showUrlInputDialog()
                }
            }
            .show()
    }

    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences("iot_prefs", MODE_PRIVATE)
        prefs.edit().putString("node_url", url).apply()
    }

    private fun getSavedUrl(): String? {
        val prefs = getSharedPreferences("iot_prefs", MODE_PRIVATE)
        return prefs.getString("node_url", null)
    }

    private fun insertSampleDataIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.letterDao()
            if (dao.getLettersByLevel(1).isEmpty()) insertLevel1Data(dao)
            if (dao.getLettersByLevel(2).isEmpty()) insertLevel2Data(dao)
            if (dao.getLettersByLevel(3).isEmpty()) insertLevel3Data(dao)
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
            val url = getSavedUrl()
            if (url != null) {
                sendToNodeMCU(selected.text, url)
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "URL이 설정되지 않았습니다.", Toast.LENGTH_SHORT).show()
                    showUrlInputDialog()
                }
            }
        }
    }

    private fun sendToNodeMCU(text: String, url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = db.letterDao()
                val entity = dao.getLetterByText(text)
                val pattern = entity?.braillePattern ?: "000000"

                val json = JSONObject().apply {
                    put("text", text)
                    put("pattern", pattern)
                }.toString()

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = json.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                // 먼저 UI에서 전송 중 메시지 표시
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "$text 전송 중...", Toast.LENGTH_SHORT).show()
                }

                val response = client.newCall(request).execute()

                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "$text 전송 성공 ✅", Toast.LENGTH_LONG).show()

                        Handler(Looper.getMainLooper()).postDelayed({
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                        }, 10_000)
                    } else {
                        Toast.makeText(this@MainActivity, "전송 실패 ❌: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "HTTP 예외 발생: ${e.message}", Toast.LENGTH_LONG).show()
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
                LetterEntity(level = 1, text = "ㄱ", braillePattern = "111011"),
                LetterEntity(level = 1, text = "ㄴ", braillePattern = "011011"),
                LetterEntity(level = 1, text = "ㄷ", braillePattern = "101011"),
                LetterEntity(level = 1, text = "ㄹ", braillePattern = "111101"),
                LetterEntity(level = 1, text = "ㅁ", braillePattern = "011101"),
                LetterEntity(level = 1, text = "ㅂ", braillePattern = "111001"),
                LetterEntity(level = 1, text = "ㅅ", braillePattern = "111110"),
                LetterEntity(level = 1, text = "ㅈ", braillePattern = "111010"),
                LetterEntity(level = 1, text = "ㅊ", braillePattern = "111100"),
                LetterEntity(level = 1, text = "ㅋ", braillePattern = "001011"),
                LetterEntity(level = 1, text = "ㅌ", braillePattern = "001011"),
                LetterEntity(level = 1, text = "ㅍ", braillePattern = "011001"),
                LetterEntity(level = 1, text = "ㅎ", braillePattern = "101001"),
                LetterEntity(level = 1, text = "ㅏ", braillePattern = "001110"),
                LetterEntity(level = 1, text = "ㅑ", braillePattern = "110001"),
                LetterEntity(level = 1, text = "ㅓ", braillePattern = "100011"),
                LetterEntity(level = 1, text = "ㅕ", braillePattern = "011100"),
                LetterEntity(level = 1, text = "ㅗ", braillePattern = "010110"),
                LetterEntity(level = 1, text = "ㅛ", braillePattern = "110010"),
                LetterEntity(level = 1, text = "ㅜ", braillePattern = "010011"),
                LetterEntity(level = 1, text = "ㅠ", braillePattern = "011010"),
                LetterEntity(level = 1, text = "ㅡ", braillePattern = "101010"),
                LetterEntity(level = 1, text = "ㅣ", braillePattern = "010101")
            )
        )
    }

    private suspend fun insertLevel2Data(dao: LetterDao) {
        dao.insertAll(
            listOf(
                LetterEntity(level = 2, text = "가", braillePattern = "111011001110"),
                LetterEntity(level = 2, text = "갸", braillePattern = "111011100011"),
                LetterEntity(level = 2, text = "거", braillePattern = "111011100011"),
                LetterEntity(level = 2, text = "겨", braillePattern = "111011011100"),
                LetterEntity(level = 2, text = "고", braillePattern = "111011010110"),
                LetterEntity(level = 2, text = "교", braillePattern = "111011110010"),
                LetterEntity(level = 2, text = "구", braillePattern = "111011010011"),
                LetterEntity(level = 2, text = "규", braillePattern = "111011011010"),
                LetterEntity(level = 2, text = "그", braillePattern = "111011101010"),
                LetterEntity(level = 2, text = "기", braillePattern = "111011010101"),
                LetterEntity(level = 2, text = "나", braillePattern = "011011001110"),
                LetterEntity(level = 2, text = "냐", braillePattern = "011011110001"),
                LetterEntity(level = 2, text = "너", braillePattern = "011011100011"),
                LetterEntity(level = 2, text = "녀", braillePattern = "011011011100"),
                LetterEntity(level = 2, text = "노", braillePattern = "011011010110"),
                LetterEntity(level = 2, text = "뇨", braillePattern = "011011110010"),
                LetterEntity(level = 2, text = "누", braillePattern = "011011010011"),
                LetterEntity(level = 2, text = "뉴", braillePattern = "011011011010"),
                LetterEntity(level = 2, text = "느", braillePattern = "011011101010"),
                LetterEntity(level = 2, text = "니", braillePattern = "011011010101"),
                LetterEntity(level = 2, text = "다", braillePattern = "101011001110"),
                LetterEntity(level = 2, text = "댜", braillePattern = "101011110001"),
                LetterEntity(level = 2, text = "더", braillePattern = "101011100011"),
                LetterEntity(level = 2, text = "뎌", braillePattern = "101011011100"),
                LetterEntity(level = 2, text = "도", braillePattern = "101011010110"),
                LetterEntity(level = 2, text = "됴", braillePattern = "101011110010"),
                LetterEntity(level = 2, text = "두", braillePattern = "101011010011"),
                LetterEntity(level = 2, text = "듀", braillePattern = "101011011010"),
                LetterEntity(level = 2, text = "드", braillePattern = "101011101010"),
                LetterEntity(level = 2, text = "디", braillePattern = "101011010101"),
                LetterEntity(level = 2, text = "라", braillePattern = "111101001110"),
                LetterEntity(level = 2, text = "랴", braillePattern = "111101110001"),
                LetterEntity(level = 2, text = "러", braillePattern = "111101100011"),
                LetterEntity(level = 2, text = "려", braillePattern = "111101011100"),
                LetterEntity(level = 2, text = "로", braillePattern = "111101010110"),
                LetterEntity(level = 2, text = "료", braillePattern = "111101110010"),
                LetterEntity(level = 2, text = "루", braillePattern = "111101010011"),
                LetterEntity(level = 2, text = "류", braillePattern = "111101011010"),
                LetterEntity(level = 2, text = "르", braillePattern = "111101101010"),
                LetterEntity(level = 2, text = "리", braillePattern = "111101010101"),
                LetterEntity(level = 2, text = "마", braillePattern = "011101001110"),
                LetterEntity(level = 2, text = "먀", braillePattern = "011101110001"),
                LetterEntity(level = 2, text = "머", braillePattern = "011101100011"),
                LetterEntity(level = 2, text = "며", braillePattern = "011101011100"),
                LetterEntity(level = 2, text = "모", braillePattern = "011101010110"),
                LetterEntity(level = 2, text = "묘", braillePattern = "011101110010"),
                LetterEntity(level = 2, text = "무", braillePattern = "011101010011"),
                LetterEntity(level = 2, text = "뮤", braillePattern = "011101011010"),
                LetterEntity(level = 2, text = "므", braillePattern = "011101101010"),
                LetterEntity(level = 2, text = "미", braillePattern = "011101010101"),
                LetterEntity(level = 2, text = "바", braillePattern = "111001001110"),
                LetterEntity(level = 2, text = "뱌", braillePattern = "111001110001"),
                LetterEntity(level = 2, text = "버", braillePattern = "111001100011"),
                LetterEntity(level = 2, text = "벼", braillePattern = "111001011100"),
                LetterEntity(level = 2, text = "보", braillePattern = "111001010110"),
                LetterEntity(level = 2, text = "뵤", braillePattern = "111001110010"),
                LetterEntity(level = 2, text = "부", braillePattern = "111001010011"),
                LetterEntity(level = 2, text = "뷰", braillePattern = "111001011010"),
                LetterEntity(level = 2, text = "브", braillePattern = "111001101010"),
                LetterEntity(level = 2, text = "비", braillePattern = "111001010101"),
                LetterEntity(level = 2, text = "사", braillePattern = "111110001110"),
                LetterEntity(level = 2, text = "샤", braillePattern = "111110110001"),
                LetterEntity(level = 2, text = "서", braillePattern = "111110100011"),
                LetterEntity(level = 2, text = "셔", braillePattern = "111110011100"),
                LetterEntity(level = 2, text = "소", braillePattern = "111110010110"),
                LetterEntity(level = 2, text = "쇼", braillePattern = "111110110010"),
                LetterEntity(level = 2, text = "수", braillePattern = "111110010011"),
                LetterEntity(level = 2, text = "슈", braillePattern = "111110011010"),
                LetterEntity(level = 2, text = "스", braillePattern = "111110101010"),
                LetterEntity(level = 2, text = "시", braillePattern = "111110010101"),
                LetterEntity(level = 2, text = "자", braillePattern = "111010001110"),
                LetterEntity(level = 2, text = "쟈", braillePattern = "111010110001"),
                LetterEntity(level = 2, text = "저", braillePattern = "111010100011"),
                LetterEntity(level = 2, text = "져", braillePattern = "111010011100"),
                LetterEntity(level = 2, text = "조", braillePattern = "111010010110"),
                LetterEntity(level = 2, text = "죠", braillePattern = "111010110010"),
                LetterEntity(level = 2, text = "주", braillePattern = "111010010011"),
                LetterEntity(level = 2, text = "쥬", braillePattern = "111010011010"),
                LetterEntity(level = 2, text = "즈", braillePattern = "111010101010"),
                LetterEntity(level = 2, text = "지", braillePattern = "111010010101"),
                LetterEntity(level = 2, text = "차", braillePattern = "111100001110"),
                LetterEntity(level = 2, text = "챠", braillePattern = "111100110001"),
                LetterEntity(level = 2, text = "처", braillePattern = "111100100011"),
                LetterEntity(level = 2, text = "쳐", braillePattern = "111100011100"),
                LetterEntity(level = 2, text = "초", braillePattern = "111100010110"),
                LetterEntity(level = 2, text = "쵸", braillePattern = "111100110010"),
                LetterEntity(level = 2, text = "추", braillePattern = "111100010011"),
                LetterEntity(level = 2, text = "츄", braillePattern = "111100011010"),
                LetterEntity(level = 2, text = "츠", braillePattern = "111100101010"),
                LetterEntity(level = 2, text = "치", braillePattern = "111100010101"),
                LetterEntity(level = 2, text = "카", braillePattern = "001011001110"),
                LetterEntity(level = 2, text = "캬", braillePattern = "001011110001"),
                LetterEntity(level = 2, text = "커", braillePattern = "001011100011"),
                LetterEntity(level = 2, text = "켜", braillePattern = "001011011100"),
                LetterEntity(level = 2, text = "코", braillePattern = "001011010110"),
                LetterEntity(level = 2, text = "쿄", braillePattern = "001011110010"),
                LetterEntity(level = 2, text = "쿠", braillePattern = "001011010011"),
                LetterEntity(level = 2, text = "큐", braillePattern = "001011011010"),
                LetterEntity(level = 2, text = "크", braillePattern = "001011101010"),
                LetterEntity(level = 2, text = "키", braillePattern = "001011010101"),
                LetterEntity(level = 2, text = "타", braillePattern = "001011001110"),
                LetterEntity(level = 2, text = "탸", braillePattern = "001011110001"),
                LetterEntity(level = 2, text = "터", braillePattern = "001011100011"),
                LetterEntity(level = 2, text = "텨", braillePattern = "001011011100"),
                LetterEntity(level = 2, text = "토", braillePattern = "001011010110"),
                LetterEntity(level = 2, text = "툐", braillePattern = "001011110010"),
                LetterEntity(level = 2, text = "투", braillePattern = "001011010011"),
                LetterEntity(level = 2, text = "튜", braillePattern = "001011011010"),
                LetterEntity(level = 2, text = "트", braillePattern = "001011101010"),
                LetterEntity(level = 2, text = "티", braillePattern = "001011010101"),
                LetterEntity(level = 2, text = "파", braillePattern = "011001001110"),
                LetterEntity(level = 2, text = "퍄", braillePattern = "011001110001"),
                LetterEntity(level = 2, text = "퍼", braillePattern = "011001100011"),
                LetterEntity(level = 2, text = "펴", braillePattern = "011001011100"),
                LetterEntity(level = 2, text = "포", braillePattern = "011001010110"),
                LetterEntity(level = 2, text = "표", braillePattern = "011001110010"),
                LetterEntity(level = 2, text = "푸", braillePattern = "011001010011"),
                LetterEntity(level = 2, text = "퓨", braillePattern = "011001011010"),
                LetterEntity(level = 2, text = "프", braillePattern = "011001101010"),
                LetterEntity(level = 2, text = "피", braillePattern = "011001010101"),
                LetterEntity(level = 2, text = "하", braillePattern = "101001001110"),
                LetterEntity(level = 2, text = "햐", braillePattern = "101001110001"),
                LetterEntity(level = 2, text = "허", braillePattern = "101001100011"),
                LetterEntity(level = 2, text = "혀", braillePattern = "101001011100"),
                LetterEntity(level = 2, text = "호", braillePattern = "101001010110"),
                LetterEntity(level = 2, text = "효", braillePattern = "101001110010"),
                LetterEntity(level = 2, text = "후", braillePattern = "101001010011"),
                LetterEntity(level = 2, text = "휴", braillePattern = "101001011010"),
                LetterEntity(level = 2, text = "흐", braillePattern = "101001101010"),
                LetterEntity(level = 2, text = "히", braillePattern = "101001010101")
            )
        )
    }

    private suspend fun insertLevel3Data(dao: LetterDao) {
        dao.insertAll(
            listOf(
                LetterEntity(level = 3, text = "가", braillePattern = "001010"),
                LetterEntity(level = 3, text = "나", braillePattern = "011011"),
                LetterEntity(level = 3, text = "다", braillePattern = "101011"),
                LetterEntity(level = 3, text = "마", braillePattern = "011010"),
                LetterEntity(level = 3, text = "바", braillePattern = "111001"),
                LetterEntity(level = 3, text = "사", braillePattern = "000111"),
                LetterEntity(level = 3, text = "자", braillePattern = "111010"),
                LetterEntity(level = 3, text = "카", braillePattern = "001011"),
                LetterEntity(level = 3, text = "타", braillePattern = "001101"),
                LetterEntity(level = 3, text = "파", braillePattern = "011001"),
                LetterEntity(level = 3, text = "하", braillePattern = "101001"),
                LetterEntity(level = 3, text = "것", braillePattern = "111000100011"),
                LetterEntity(level = 3, text = "억", braillePattern = "011000"),
                LetterEntity(level = 3, text = "언", braillePattern = "100000"),
                LetterEntity(level = 3, text = "얼", braillePattern = "100001"),
                LetterEntity(level = 3, text = "연", braillePattern = "011110"),
                LetterEntity(level = 3, text = "열", braillePattern = "001100"),
                LetterEntity(level = 3, text = "영", braillePattern = "001000"),
                LetterEntity(level = 3, text = "옥", braillePattern = "010010"),
                LetterEntity(level = 3, text = "운", braillePattern = "001001"),
                LetterEntity(level = 3, text = "온", braillePattern = "000100"),
                LetterEntity(level = 3, text = "을", braillePattern = "100010"),
                LetterEntity(level = 3, text = "은", braillePattern = "010100"),
                LetterEntity(level = 3, text = "인", braillePattern = "000001"),
                LetterEntity(level = 3, text = "그래서", braillePattern = "011111100011"),
                LetterEntity(level = 3, text = "그러나", braillePattern = "011111011011"),
                LetterEntity(level = 3, text = "그러면", braillePattern = "011111101101"),
                LetterEntity(level = 3, text = "그러므로", braillePattern = "011111101110"),
                LetterEntity(level = 3, text = "그런데", braillePattern = "011111010001"),
                LetterEntity(level = 3, text = "그리고", braillePattern = "011111010110"),
                LetterEntity(level = 3, text = "그리하여", braillePattern = "011111011100")
            )
        )
    }
}
