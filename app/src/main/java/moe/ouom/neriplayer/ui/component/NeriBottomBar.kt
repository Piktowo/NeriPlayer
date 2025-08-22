package moe.ouom.neriplayer.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import moe.ouom.neriplayer.navigation.Destinations
import moe.ouom.neriplayer.util.performHapticFeedback

@Composable
fun NeriBottomBar(
    items: List<Pair<Destinations, ImageVector>>,
    currentDestination: NavDestination?,
    onItemSelected: (Destinations) -> Unit,
    modifier: Modifier = Modifier,
    selectAlpha: Float = 1f
) {
    val context = LocalContext.current
    val alwaysShowLabel = selectAlpha != 0f

    NavigationBar(
        modifier = modifier.background(Color.Transparent),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { (dest, icon) ->
            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    context.performHapticFeedback()
                    onItemSelected(dest)
                },
                icon = { Icon(icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                alwaysShowLabel = alwaysShowLabel,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = selectAlpha),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}