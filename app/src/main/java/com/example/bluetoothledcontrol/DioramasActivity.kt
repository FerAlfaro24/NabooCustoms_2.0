package com.example.bluetoothledcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DioramasActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dioramas)

        findViewById<Button>(R.id.btnDiorama1).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
} 