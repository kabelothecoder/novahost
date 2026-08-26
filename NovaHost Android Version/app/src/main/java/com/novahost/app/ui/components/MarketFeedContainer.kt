package com.novaedge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class NewsItem(
    val id: String,
    val timestamp: Long,
    val headline: String,
    val impact: ImpactLevel
)

enum class ImpactLevel {
    CRITICAL, POSITIVE, NEUTRAL
}

@Composable
fun MarketFeedContainer(modifier: Modifier = Modifier) {
    var newsList by remember { mutableStateOf(generateMockNews()) }

    // Mock live feed updater
    LaunchedEffect(Unit) {
        while(true) {
            delay(15000L) // update every 15s
            val newFeed = generateMockNews()
            newsList = newFeed
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "TOP STORIES",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Dynamic scrolling list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp), // fixed height to avoid infinite scroll conflicts
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(newsList) { news ->
                NewsFeedCard(newsItem = news)
            }
        }
    }
}

@Composable
fun NewsFeedCard(newsItem: NewsItem) {
    val impactColor = when(newsItem.impact) {
        ImpactLevel.CRITICAL -> Color(0xFFFF3B30)
        ImpactLevel.POSITIVE -> Color(0xFF34C759)
        ImpactLevel.NEUTRAL -> Color(0xFF8E8E93)
    }

    val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(newsItem.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Impact Indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(impactColor)
                .align(Alignment.CenterVertically)
        )

        Column {
            Text(
                text = timeString,
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = newsItem.headline,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

fun generateMockNews(): List<NewsItem> {
    val current = System.currentTimeMillis()
    return listOf(
        NewsItem("1", current - 120000, "Federal Reserve indicates potential rate hold, XAUUSD tests key resistance.", ImpactLevel.CRITICAL),
        NewsItem("2", current - 450000, "European Central Bank aligns with dovish sentiment. EURUSD bounces.", ImpactLevel.POSITIVE),
        NewsItem("3", current - 980000, "Global supply chain constraints ease slightly in tech sectors.", ImpactLevel.NEUTRAL),
        NewsItem("4", current - 1500000, "US Non-Farm Payrolls significantly exceed expectations.", ImpactLevel.CRITICAL),
        NewsItem("5", current - 3600000, "Oil prices surge following unexpected production cuts.", ImpactLevel.CRITICAL)
    )
}
