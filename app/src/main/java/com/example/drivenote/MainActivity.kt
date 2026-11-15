package com.example.drivenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.drivenote.ui.theme.DriveNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriveNoteTheme(darkTheme = false) { DriveNoteApp() }
        }
    }
}