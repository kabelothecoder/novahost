package com.novaedge.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaedge.app.ui.theme.ActiveGrey
import com.novaedge.app.ui.theme.Crimson
import com.novaedge.app.ui.viewmodels.SubscriptionRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallOverlay(
    primaryColor: Color,
    subscriptionRoute: SubscriptionRoute,
    checkoutUrl: String?,
    errorMessage: String?,
    onCheckStatus: (String) -> Unit,
    onUnlocked: () -> Unit,
    onReset: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current

    val openCheckoutUrl = { url: String ->
        try {
            // Save email so MainActivity can retrieve it when the deep link returns
            context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("pending_payment_email", email.trim()).apply()

            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to normal browser if Custom Tabs fail
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "UNLOCK Nova Edge",
                color = Color(0xFF1A1D20),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your email to verify your subscription or activate a new license.",
                color = Color(0xFF8A94A6),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            when (subscriptionRoute) {
                SubscriptionRoute.IDLE, SubscriptionRoute.CHECKING, SubscriptionRoute.ERROR -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Email address", color = Color(0xFF8A94A6)) },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = primaryColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF1A1D20),
                            unfocusedTextColor = Color(0xFF1A1D20),
                            cursorColor = primaryColor
                        ),
                        shape = CircleShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = subscriptionRoute != SubscriptionRoute.CHECKING
                    )
                    
                    if (subscriptionRoute == SubscriptionRoute.ERROR && errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Crimson, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(errorMessage, color = Crimson, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { if (email.isNotBlank()) onCheckStatus(email) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C9CE6)),
                        shape = CircleShape,
                        enabled = subscriptionRoute != SubscriptionRoute.CHECKING
                    ) {
                        if (subscriptionRoute == SubscriptionRoute.CHECKING) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("CHECK STATUS", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }

                SubscriptionRoute.ACTIVE_SAME_DEVICE, SubscriptionRoute.PAYMENT_VERIFIED -> {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Welcome Back!", color = Color(0xFF1A1D20), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Subscription verified on this device.", color = Color(0xFF8A94A6), fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = CircleShape
                    ) {
                        Text(
                            if (subscriptionRoute == SubscriptionRoute.PAYMENT_VERIFIED) "WELCOME! ENTER TERMINAL"
                            else "ENTER TERMINAL",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                SubscriptionRoute.VERIFYING_PAYMENT -> {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = primaryColor,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Verifying Payment...",
                        color = Color(0xFF1A1D20),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Please wait while we confirm your payment with Payfast.",
                        color = Color(0xFF8A94A6),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                SubscriptionRoute.NEW_USER -> {
                    Text("Monthly Subscription", color = Color(0xFF1A1D20), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("R250 / month", color = primaryColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Full access to AI analysis, live signals, and market pulse.", color = Color(0xFF8A94A6), fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { checkoutUrl?.let { openCheckoutUrl(it) } },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C9CE6)),
                        shape = CircleShape
                    ) {
                        Text("SUBSCRIBE NOW", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onReset) {
                        Text("Change Email", color = Color(0xFF8A94A6), fontSize = 12.sp, textDecoration = TextDecoration.Underline)
                    }
                }

                SubscriptionRoute.ACTIVE_NEW_DEVICE -> {
                    Text("Device Mismatch", color = Crimson, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("This subscription is locked to another device. You can reactivate it on this device for a one-time fee.", color = Color(0xFF8A94A6), fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("Reactivation Fee: R150", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { checkoutUrl?.let { openCheckoutUrl(it) } },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C9CE6)),
                        shape = CircleShape
                    ) {
                        Text("REACTIVATE DEVICE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onReset) {
                        Text("Change Email", color = Color(0xFF8A94A6), fontSize = 12.sp, textDecoration = TextDecoration.Underline)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            // Support Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@novaedge.co")
                        putExtra(Intent.EXTRA_SUBJECT, "Nova Edge Support Request")
                    }
                    context.startActivity(Intent.createChooser(intent, "Email Support"))
                }.padding(8.dp)
            ) {
                Icon(Icons.Rounded.SupportAgent, contentDescription = null, tint = Color(0xFF8A94A6), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Need help? support@novaedge.co", color = Color(0xFF8A94A6), fontSize = 11.sp, textDecoration = TextDecoration.Underline)
            }
        }
    }
}
