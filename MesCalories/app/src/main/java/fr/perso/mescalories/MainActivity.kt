package fr.perso.mescalories

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.perso.mescalories.ui.AppNavigation
import fr.perso.mescalories.ui.theme.MesCaloriesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MesCaloriesTheme {
                AppNavigation()
            }
        }
    }
}
