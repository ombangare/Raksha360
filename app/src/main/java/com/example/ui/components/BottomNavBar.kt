package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.LanguageStrings
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.NavyDark
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted

data class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun NaariKavachBottomNavBar(
    currentRoute: String,
    strings: LanguageStrings,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(NavRoutes.HOME, "Home", Icons.Default.Home, "nav_home"),
        NavItem(NavRoutes.MAP, "Map", Icons.Default.Map, "nav_map"),
        NavItem(NavRoutes.JOURNEY, "Journey", Icons.Default.Navigation, "nav_journey"),
        NavItem(NavRoutes.THREATS, "Intel", Icons.Default.Security, "nav_threats"),
        NavItem(NavRoutes.PRIVACY, "Privacy", Icons.Default.Lock, "nav_privacy")
    )

    NavigationBar(
        modifier = modifier,
        containerColor = NavyDark,
        tonalElevation = 8.dp
    ) {
        items.forEach { item ->
            val isSelected = when (item.route) {
                NavRoutes.JOURNEY -> currentRoute == NavRoutes.JOURNEY || currentRoute == NavRoutes.ACTIVE_JOURNEY
                NavRoutes.PRIVACY -> currentRoute == NavRoutes.PRIVACY || currentRoute == NavRoutes.BACKEND_SETTINGS
                else -> currentRoute == item.route
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = ShieldBlue,
                    unselectedIconColor = Color(0xFF94A3B8),
                    unselectedTextColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}
