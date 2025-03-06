package com.example.bluetoothledcontrol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import android.widget.MediaController
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity

class IntroVideoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_video)

        val videoView = findViewById<VideoView>(R.id.videoView)
        val path = "android.resource://" + packageName + "/raw/videointro"
        videoView.setVideoURI(Uri.parse(path))
        
        videoView.setOnCompletionListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        
        videoView.start()
    }
} 