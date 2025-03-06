package com.example.bluetoothledcontrol

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val cbRemember = findViewById<CheckBox>(R.id.cbRemember)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // Cargar datos guardados
        if (sharedPreferences.getBoolean("remember", false)) {
            etUsername.setText(sharedPreferences.getString("username", ""))
            etPassword.setText(sharedPreferences.getString("password", ""))
            cbRemember.isChecked = true
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            if (username == "usuario1" && password == "1234") {
                // Guardar preferencias si está marcado "recordarme"
                if (cbRemember.isChecked) {
                    sharedPreferences.edit().apply {
                        putString("username", username)
                        putString("password", password)
                        putBoolean("remember", true)
                        apply()
                    }
                } else {
                    sharedPreferences.edit().clear().apply()
                }

                startActivity(Intent(this, MainMenuActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 