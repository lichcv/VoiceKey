package com.duong.voicekey

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class VoiceIME : InputMethodService() {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var recording = false
    private lateinit var mic: Button
    private lateinit var status: TextView
    private val ui = Handler(Looper.getMainLooper())

    private fun cfg(k: String) = getSharedPreferences("cfg", MODE_PRIVATE).getString(k, "") ?: ""

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F0F2F5")); setPadding(16, 12, 16, 12)
        }
        status = TextView(this).apply { text = "Chạm micro để nói"; gravity = Gravity.CENTER; textSize = 14f }
        mic = Button(this).apply { text = "🎤  Nói"; textSize = 22f; setOnClickListener { toggle() } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun key(t: String, f: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(key("⌫") { currentInputConnection?.deleteSurroundingText(1, 0) })
        row.addView(key(".") { currentInputConnection?.commitText(".", 1) })
        row.addView(key(",") { currentInputConnection?.commitText(",", 1) })
        row.addView(key("↵") { currentInputConnection?.commitText("\n", 1) })
        row.addView(key("🌐") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(status); root.addView(mic); root.addView(row)
        return root
    }

    private fun toggle() {
        if (!recording) start() else stop()
    }

    private fun start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Chưa có quyền micro. Mở app 'Giọng Nói' để cấp quyền."; return
        }
        if (cfg("key").isEmpty()) { status.text = "Chưa nhập API key trong app 'Giọng Nói'."; return }
        try {
            val f = File(cacheDir, "v.m4a"); file = f
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000); r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath); r.prepare(); r.start()
            rec = r; recording = true
            mic.text = "⏹  Dừng"; status.text = "🔴 Đang ghi âm..."
        } catch (e: Exception) { status.text = "Lỗi micro: ${e.message}" }
    }

    private fun stop() {
        recording = false; mic.text = "🎤  Nói"
        try { rec?.stop() } catch (e: Exception) { status.text = "Ghi âm quá ngắn"; rec?.release(); rec = null; return }
        rec?.release(); rec = null
        val f = file ?: return
        status.text = "⏳ Đang nhận diện..."
        Thread {
            try {
                val raw = transcribe(f)
                if (raw.isBlank()) { ui.post { status.text = "Không nghe thấy gì" }; return@Thread }
                ui.post { status.text = "✍️ Đang sửa chính tả..." }
                val fixed = try { fix(raw) } catch (e: Exception) { raw }
                ui.post { currentInputConnection?.commitText(fixed, 1); status.text = "✅ Xong. Chạm micro để nói tiếp" }
            } catch (e: Exception) { ui.post { status.text = "❌ Lỗi: ${e.message}" } }
        }.start()
    }

    private fun readResp(c: HttpURLConnection): String {
        val code = c.responseCode
        val s = if (code < 400) c.inputStream else c.errorStream
        val t = s?.bufferedReader()?.readText() ?: ""
        if (code >= 400) throw Exception("HTTP $code")
        return t
    }

    private fun transcribe(f: File): String {
        val b = "----" + UUID.randomUUID()
        val c = URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer ${cfg("key")}")
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$b")
        c.outputStream.use { o ->
            fun field(n: String, v: String) =
                o.write("--$b\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray())
            field("model", "whisper-large-v3"); field("language", "vi")
            o.write("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"v.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n".toByteArray())
            o.write(f.readBytes()); o.write("\r\n--$b--\r\n".toByteArray())
        }
        return JSONObject(readResp(c)).getString("text").trim()
    }

    private fun fix(text: String): String {
        var sys = "Bạn là chuyên gia biên tập văn bản tiếng Việt. Sửa lỗi chính tả, lỗi nhận dạng giọng nói, dấu câu và viết hoa. Giữ nguyên ý, không thêm bớt. CHỈ TRẢ VỀ VĂN BẢN ĐÃ SỬA, không giải thích."
        if (cfg("dict").isNotEmpty()) sys += " Các từ riêng cần viết đúng: ${cfg("dict")}."
        val body = JSONObject().put("model", "llama-3.3-70b-versatile").put("temperature", 0.1).put(
            "messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", text))
        )
        val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Authorization", "Bearer ${cfg("key")}")
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        return JSONObject(readResp(c)).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }
}
