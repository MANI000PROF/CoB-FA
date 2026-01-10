package com.cobfa.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import com.cobfa.app.navigation.AppNavigation
import com.cobfa.app.utils.GamificationScheduler
import com.google.firebase.FirebaseApp

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        GamificationScheduler.schedule(this)

        setContent {
            AppNavigation()
        }
    }
}
