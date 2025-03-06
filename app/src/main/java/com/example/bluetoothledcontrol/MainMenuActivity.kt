package com.example.bluetoothledcontrol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class MainMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        findViewById<Button>(R.id.btnNavesYDioramas).setOnClickListener {
            startActivity(Intent(this, SelectionActivity::class.java))
        }

        findViewById<Button>(R.id.btnCatalogo).setOnClickListener {
            openUrl("https://drive.google.com/drive/folders/1N8tm5FXWINmWiWu97AnmgC_-hawg2Mv1?usp=drive_link")
        }

        findViewById<ImageButton>(R.id.btnInstagram).setOnClickListener {
            openUrl("https://www.instagram.com/naboo.customs/?hl=es-la")
        }

        findViewById<ImageButton>(R.id.btnFacebook).setOnClickListener {
            openUrl("https://www.facebook.com/Nabbo.customs/")
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
} 