package com.duong.voicekey

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        fun tv(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, 24, 0, 8) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        val key = EditText(this).apply {
            hint = "Dán API key Groq (gsk_...)"; setText(p.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dict = EditText(this).apply {
            hint = "Từ riêng cần viết đúng (phân cách bằng dấu phẩy)"; setText(p.getString("dict", ""))
        }
        root.addView(tv("Bước 1: nhập khóa API (lưu trong máy, không gửi đi đâu khác)")); root.addView(key)
        root.addView(tv("Từ riêng (tên dự án, thuật ngữ...) - không bắt buộc")); root.addView(dict)
        root.addView(btn("Lưu cài đặt") {
            p.edit().putString("key", key.text.toString().trim()).putString("dict", dict.text.toString().trim()).apply()
            Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show()
        })
        root.addView(tv("Bước 2: cấp quyền micro"))
        root.addView(btn("Cấp quyền micro") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) })
        root.addView(tv("Bước 3: bật bàn phím 'Bàn phím Giọng Nói' trong danh sách"))
        root.addView(btn("Mở cài đặt bàn phím") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(tv("Bước 4: chọn dùng bàn phím này"))
        root.addView(btn("Chọn bàn phím") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
