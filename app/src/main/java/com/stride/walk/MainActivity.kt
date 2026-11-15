package com.stride.walk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WalkRepo.init(this)
        Notif.ensureChannels(this)
        setContent {
            StrideTheme { StrideRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (WalkState.live.value.phase == Phase.IDLE) QuickStart.show(this)
    }
}
