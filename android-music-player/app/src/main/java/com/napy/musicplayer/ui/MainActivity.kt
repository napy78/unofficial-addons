package com.napy.musicplayer.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        setContent {
            NapyTheme {
                var granted by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED)
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    granted = it[audioPerm] == true
                }
                LaunchedEffect(Unit) {
                    if (!granted) {
                        val perms = mutableListOf(audioPerm)
                        if (Build.VERSION.SDK_INT >= 33) {
                            perms += Manifest.permission.POST_NOTIFICATIONS
                            perms += Manifest.permission.READ_MEDIA_IMAGES   // cover.jpg nelle cartelle
                        }
                        launcher.launch(perms.toTypedArray())
                    }
                }
                // aggiornamento automatico del database a ogni apertura
                LaunchedEffect(granted) { if (granted) vm.rescan() }
                AppScreen(vm, granted) { launcher.launch(arrayOf(audioPerm)) }
            }
        }
    }
}
