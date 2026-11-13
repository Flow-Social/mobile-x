package me.floow.shared.smoke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.theme.ChetwodeBlue300
import me.floow.uikit.theme.ChetwodeBlue500
import me.floow.uikit.theme.ChetwodeBlue700
import me.floow.uikit.theme.FlowTheme
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.theme.MoodyBlue400
import me.floow.uikit.theme.MoodyBlue700
import me.floow.uikit.theme.NinehedronShape
import me.floow.uikit.theme.rememberMontserratFontFamily

@Composable
fun DesignSystemSmokeScreen() {
    var isDark by remember { mutableStateOf(false) }

    FlowTheme(darkTheme = isDark) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val montserrat = rememberMontserratFontFamily()

                    Text(
                        text = "Flow Design System",
                        style = LocalTypography.current.profileName.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    Text(
                        text = "iOS + Android Shared Compose UI",
                        fontFamily = montserrat,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = ChetwodeBlue500
                    )
                }

                // Theme Mode Switcher
                Button(
                    onClick = { isDark = !isDark },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) ChetwodeBlue500 else Color(0xFF1B1B21),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                        fontFamily = rememberMontserratFontFamily(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // FlowStarShape (Ninehedron) Showcase
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "FlowStarShape (9-pointed Star)",
                            style = LocalTypography.current.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary gradient star
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(NinehedronShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(ChetwodeBlue500, ChetwodeBlue700)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }

                            // Moody blue star
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(NinehedronShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(MoodyBlue400, MoodyBlue700)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(NinehedronShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                // Typography Showcase (Accent Montserrat + Native Content)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Accent Typography (Montserrat)",
                            style = LocalTypography.current.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Text(
                            text = "Montserrat Black 24sp",
                            style = LocalTypography.current.profileName.copy(
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Native Content Typography (System / Roboto)",
                            style = LocalTypography.current.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Text(
                            text = "Title Large 20sp Black",
                            style = LocalTypography.current.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Text(
                            text = "Title Medium 16sp Medium",
                            style = LocalTypography.current.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Text(
                            text = "Body Medium 15sp — Native system font on iOS (SF Pro) / Roboto on Android for chats & feeds.",
                            style = LocalTypography.current.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        )
                    }
                }

                // Palette Colors Showcase
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Brand Palette (Chetwode & Moody)",
                            style = LocalTypography.current.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ColorSwatch(name = "Blue300", color = ChetwodeBlue300)
                            ColorSwatch(name = "Blue500", color = ChetwodeBlue500)
                            ColorSwatch(name = "Blue700", color = ChetwodeBlue700)
                            ColorSwatch(name = "Moody400", color = MoodyBlue400)
                            ColorSwatch(name = "Moody700", color = MoodyBlue700)
                        }
                    }
                }

                // Interactive Production Button Style
                Button(
                    onClick = { },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChetwodeBlue500,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Production Flow Pill Button",
                        fontFamily = rememberMontserratFontFamily(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(name: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
        )
        Text(
            text = name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
