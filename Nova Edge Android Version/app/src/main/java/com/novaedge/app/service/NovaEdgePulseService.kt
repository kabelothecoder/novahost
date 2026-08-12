package com.novaedge.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.PowerManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.LruCache
import android.view.Gravity
import com.novaedge.app.sdk.NotificationHelper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.novaedge.app.R
import com.novaedge.app.ui.theme.Cyan
import com.novaedge.app.ui.theme.NovaEdgeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.Build
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import com.novaedge.app.sdk.SupabaseSetup
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.flow.collect
import com.novaedge.app.sdk.TradeSignal
import com.novaedge.app.sdk.MetaAPIManager


@Serializable
data class TradeLog(
    val license_key: String,
    val pair: String,
    val action: String,
    val pl: Double
)

class NovaEdgePulseService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private var wakeLock: PowerManager.WakeLock? = null
    private var isOverlayAdded = false
    private lateinit var params: WindowManager.LayoutParams
    
    // Deduplication store for signal IDs (prevents double trades on socket bounce)
    private val processedSignals = LruCache<String, Boolean>(100)
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // Lifecycle requirements for Compose View in a WindowManager
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        
        // Acquire WakeLock to prevent Deep Doze from killing socket
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaEdge::PulseLock").apply {
            acquire(12 * 60 * 60 * 1000L) // 12 hours max
        }

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        startForegroundServiceNotification()
        setupPulseOverlay()
        
        serviceScope.launch {
            MetaAPIManager.botStatus.collect { status ->
                when (status) {
                    com.novaedge.app.sdk.BotStatus.RUNNING -> {
                        if (!isOverlayAdded) {
                            try {
                                windowManager.addView(composeView, params)
                                isOverlayAdded = true
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    com.novaedge.app.sdk.BotStatus.STOPPED, com.novaedge.app.sdk.BotStatus.IDLE -> {
                        if (isOverlayAdded) {
                            try {
                                windowManager.removeView(composeView)
                                isOverlayAdded = false
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }
            }
        }
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "pulse_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pulse Bubble", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Nova Edge Pulse Active")
            .setContentText("Monitoring live trading signals.")
            .setSmallIcon(R.drawable.app_logo)
            .build()
        startForeground(2, notification)
    }

    private fun setupPulseOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NovaEdgePulseService)
            setViewTreeViewModelStoreOwner(this@NovaEdgePulseService)
            setViewTreeSavedStateRegistryOwner(this@NovaEdgePulseService)
            setContent {
                NovaEdgeTheme {
                    PulseApp(
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(this, params)
                        },
                        onSnapEdges = {
                            val metrics = DisplayMetrics()
                            windowManager.defaultDisplay.getMetrics(metrics)
                            val screenWidth = metrics.widthPixels
                            val targetX = if (params.x < screenWidth / 2) 0 else screenWidth
                            
                            // Smoother snapped animation using quadratic easing
                            CoroutineScope(Dispatchers.Main).launch {
                                val startX = params.x
                                val duration = 250L
                                val startTime = System.currentTimeMillis()
                                
                                while (System.currentTimeMillis() - startTime < duration) {
                                    val elapsed = (System.currentTimeMillis() - startTime).toFloat() / duration
                                    // Ease out cubic
                                    val t = 1f - Math.pow(1.0 - elapsed, 3.0).toFloat()
                                    params.x = (startX + (targetX - startX) * t).toInt()
                                    try {
                                        windowManager.updateViewLayout(composeView, params)
                                    } catch (e: Exception) { break }
                                    delay(8)
                                }
                                params.x = targetX
                                windowManager.updateViewLayout(composeView, params)
                            }
                        },
                        closeService = {
                            stopSelf()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        // Finalize broker connection
        serviceScope.launch {
            MetaAPIManager.disconnect("trade245_sub_001")
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        if (::composeView.isInitialized && isOverlayAdded) {
            try {
                windowManager.removeView(composeView)
                isOverlayAdded = false
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun PulseApp(
    onDrag: (Float, Float) -> Unit,
    onSnapEdges: () -> Unit,
    closeService: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var signalActive by remember { mutableStateOf(false) }
    var currentPair by remember { mutableStateOf("XAUUSD") }
    
    val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
    val avatarUrl = prefs.getString("avatar_url", null)
    val displayName = prefs.getString("display_name", "TRADING BOT") ?: "TRADING BOT"
    
    val processedSignals = remember { LruCache<String, Boolean>(100) }

    // Real-time signal subscription
    LaunchedEffect(Unit) {
        try {
            val channel = SupabaseSetup.client.realtime.channel("signals_channel")
            val signalFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "signals"
            }
    
            channel.subscribe()
    
            signalFlow.collect { action ->
                val signal = action.decodeRecord<TradeSignal>()
    
                // 1. SIGNAL DEDUPLICATION CHECK
                val signalId = signal.id ?: return@collect
                if (processedSignals.get(signalId) != null) return@collect
                processedSignals.put(signalId, true)
    
                // 2. FILTERING LOGIC
                val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                val rawSymbols = prefs.getString("allowed_symbols", "") ?: ""
                val allowedSymbols = if (rawSymbols.isEmpty()) emptyList() else rawSymbols.split(",")

                // 2a. ROBOT OWNERSHIP CHECK (multi-tenancy).
                // This device is licensed to exactly one robot. Signals are
                // tagged with the robot that issued them, so anything from a
                // different robot must be dropped -- otherwise a user would
                // execute trades from a mentor they never paid.
                val activeEaId = prefs.getString("active_ea_id", "") ?: ""
                val signalEaId = signal.ea_id ?: ""
                if (activeEaId.isNotEmpty() && signalEaId.isNotEmpty() && activeEaId != signalEaId) {
                    android.util.Log.d("Nova Edge", "PULSE: ignoring signal for robot $signalEaId (this device runs $activeEaId)")
                    return@collect
                }

                if (allowedSymbols.isEmpty() || allowedSymbols.contains(signal.pair)) {
                    currentPair = signal.pair
                    signalActive = true
                    expanded = true
    
                    // 3. ACTUAL TRADE EXECUTION
                    val userBalance = MetaAPIManager.balance.value
                    val adminBalance = signal.adminBalance ?: userBalance // fallback if not provided
                    val signalLot = signal.lot ?: 0.01
    
                    // Dynamic lot sizing math: scaling volume based on ratio
                    var calculatedLot = if (adminBalance > 0.0) {
                        (userBalance / adminBalance) * signalLot
                    } else {
                        0.01
                    }
    
                    // Enforce 0.01 minimums
                    if (calculatedLot < 0.01) calculatedLot = 0.01
    
                    // Round to 2 decimal places
                    val volume = kotlin.math.round(calculatedLot * 100.0) / 100.0
    
                    // Execute via MetaCopier. Broker credentials and the
                    // MetaCopier account id stay server-side -- the licence is
                    // the only thing this device sends.
                    val tradeResult = MetaAPIManager.executeTrade(
                        context = context,
                        pair = signal.pair,
                        side = signal.action,
                        volume = volume,
                        sl = signal.sl,
                        tp = signal.tp,
                        signalId = signal.signal_id ?: signal.id
                    )

                    // 4. NOTIFY -- only when the trade actually went through.
                    // Announcing an execution that failed is worse than silence:
                    // the user believes they are in a position they do not hold.
                    tradeResult
                        .onSuccess {
                            NotificationHelper.showTradeNotification(
                                context = context,
                                pair = signal.pair,
                                action = signal.action,
                                price = "Current Market"
                            )
                        }
                        .onFailure { err ->
                            android.util.Log.e("Nova Edge", "PULSE_ERROR: execution failed - ${err.message}")
                            NotificationHelper.showTradeFailedNotification(
                                context = context,
                                pair = signal.pair,
                                reason = err.message ?: "Trade could not be placed."
                            )
                        }
    
                    // Auto-dismiss after 10 seconds
                    delay(10000)
                    signalActive = false
                    expanded = false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Nova Edge", "PULSE_ERROR: Realtime connection dropped", e)
        }
    }


    val bubbleSize by animateFloatAsState(targetValue = if (expanded) 360f else 64f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    val bubbleHeight by animateFloatAsState(targetValue = if (expanded) 240f else 64f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))

    Box(
        modifier = Modifier
            .size(width = bubbleSize.dp, height = bubbleHeight.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onSnapEdges() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!expanded) { 
                            onDrag(dragAmount.x, dragAmount.y) 
                        }
                    }
                )
            }
    ) {
        if (!expanded) {
            // Idle Bubble
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(16.dp, CircleShape, spotColor = Cyan)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(2.dp, Cyan, CircleShape)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Pulse",
                    modifier = Modifier.size(40.dp).padding(4.dp)
                )
                // Neon pulse dot
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(8.dp)
                    .shadow(8.dp, CircleShape, spotColor = Color.Green)
                    .clip(CircleShape)
                    .background(Color.Green))
            }
        } else {
            // Expanded HUD (Glassmorphism Asymmetric Grid)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(32.dp, RoundedCornerShape(16.dp), spotColor = Cyan)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f)) // Frosted alpha base
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, Cyan, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = "Pulse",
                                    modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, Cyan, CircleShape)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(displayName.uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.Green).shadow(4.dp, spotColor=Color.Green))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scanning Markets", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Minimize button
                            Box(modifier = Modifier.clickable { expanded = false }.padding(4.dp)) {
                                Text("[ — ]", color = Color.White.copy(alpha=0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Split Console Grid
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Left Panel (Terminal Feed)
                        Box(modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha=0.5f))
                            .border(1.dp, Color.White.copy(alpha=0.08f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                                // Real-time feed simulation/placeholder
                                items(4) { idx ->
                                    val logText = when(idx) {
                                        0 -> if (signalActive) "[BOT] Bot Active" else "[BOT] Running..."
                                        1 -> "[MT5] Connection OK"
                                        2 -> "[SYS] Market Data Sync"
                                        else -> "[INIT] Nova Edge Neural Core"
                                    }
                                    Text(logText, color = Color.Green, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                        
                        // Right Panel (Performance Matrix)
                        Column(modifier = Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBlock("TOTAL EVENTS", "14", modifier = Modifier.weight(1f))
                            StatBlock("ACTIVE SIGNALS", if (signalActive) "1" else "0", modifier = Modifier.weight(1f))
                            StatBlock("LATENCY", "42ms", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBlock(title: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E).copy(alpha=0.6f))
            .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp)), 
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White.copy(alpha=0.5f), fontSize = 7.sp, letterSpacing = 0.5.sp)
        }
    }
}
