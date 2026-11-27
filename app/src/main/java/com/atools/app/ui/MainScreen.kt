package com.atools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.atools.app.R
import com.atools.app.ui.countdown.CountdownScreen

private data class TabItem(
    val titleRes: Int,
    val iconRes: Int,
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val tabs = listOf(
        TabItem(R.string.tab_countdown, R.drawable.ic_tab_countdown),
        TabItem(R.string.tab_tools, R.drawable.ic_tab_tools),
        TabItem(R.string.tab_settings, R.drawable.ic_tab_settings),
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = context.getString(tab.titleRes),
                            )
                        },
                        label = { Text(context.getString(tab.titleRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> CountdownScreen(modifier = Modifier.padding(padding))
            else -> PlaceholderScreen(
                title = context.getString(tabs[selectedTab].titleRes),
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = context.getString(R.string.coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
