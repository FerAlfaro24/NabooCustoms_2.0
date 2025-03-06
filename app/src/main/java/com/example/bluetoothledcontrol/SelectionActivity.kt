package com.example.bluetoothledcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection)

        findViewById<Button>(R.id.btnNaves).setOnClickListener {
            startActivity(Intent(this, NavesActivity::class.java))
        }

        findViewById<Button>(R.id.btnDioramas).setOnClickListener {
            startActivity(Intent(this, DioramasActivity::class.java))
        }
    }
} 