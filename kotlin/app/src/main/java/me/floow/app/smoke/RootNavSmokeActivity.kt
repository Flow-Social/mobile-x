package me.floow.app.smoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.floow.app.FlowApp

class RootNavSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        me.floow.database.AndroidDatabaseContext.set(this)

        val prefs = getSharedPreferences("flowme.auth", android.content.Context.MODE_PRIVATE)
        if (!prefs.contains("authToken")) {
            prefs.edit()
                .putString("authToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiMjEiLCJlbWFpbCI6InNtb2tlX3Rlc3RAZmxvdy5tZSIsInRva2VuX3ZlcnNpb24iOjEsImV4cCI6MTc5NTcwMjc2MywiaWF0IjoxNzkwNTE4NzYzfQ.emtHgzgDTcVjWZPu_Y0B4NcaBNoMTwwlOTpY68tbLY4")
                .putString("authUserId", "21")
                .apply()
        }

        enableEdgeToEdge()
        setContent {
            FlowApp()
        }
    }
}
