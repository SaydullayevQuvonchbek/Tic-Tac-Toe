package com.example.tictactoe

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.system.exitProcess

class CrashReporterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scrollView = ScrollView(this)
        val textView = TextView(this)
        textView.text = intent.getStringExtra("CRASH_LOG")
        textView.textSize = 14f
        textView.setPadding(32, 32, 32, 32)
        scrollView.addView(textView)
        setContentView(scrollView)
    }
}

class TicTacToeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val stackTrace = throwable.stackTraceToString()
            
            val intent = Intent(this, CrashReporterActivity::class.java).apply {
                putExtra("CRASH_LOG", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }
}
