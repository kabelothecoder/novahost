package com.novahost.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novahost.app.R
import com.novahost.app.navigation.Routes
import com.novahost.app.ui.components.GlassCard
import com.novahost.app.ui.components.GradientButton
import com.novahost.app.ui.theme.*
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(val id: String, val avatar_url: String? = null)

private enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT }

@Composable
fun AuthScreen(navController: NavController) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    val themeState = LocalNovaHostTheme.current
    val primaryColor = themeState.primaryColor

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Scrim layer so text is readable over the Main Activity Video Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))

            // Premium Hero Logo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .shadow(16.dp, CircleShape, ambientColor = primaryColor, spotColor = primaryColor)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(2.dp, primaryColor, CircleShape)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.novahost_mark),
                    contentDescription = "Nexus Logo",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "NovaHost NEXUS",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                letterSpacing = 2.sp
            )
            Text(
                "AI Trading Command System",
                color = ActiveGrey,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(48.dp))

            // Auth Glass Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    text = when (mode) {
                        AuthMode.SIGN_IN -> "ACCESS TERMINAL"
                        AuthMode.SIGN_UP -> "REGISTER LICENSE"
                        AuthMode.FORGOT -> "RECOVER ACCESS"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(24.dp))

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "authContent"
                ) { targetMode ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (targetMode) {
                            AuthMode.SIGN_IN -> SignInForm(navController)
                            AuthMode.SIGN_UP -> SignUpForm()
                            AuthMode.FORGOT -> ForgotForm()
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Mode switcher links inside GlassCard footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (mode != AuthMode.SIGN_IN) {
                        Text(
                            "Sign In",
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { mode = AuthMode.SIGN_IN }.padding(8.dp)
                        )
                    }
                    if (mode != AuthMode.SIGN_UP) {
                        Text(
                            "Register",
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { mode = AuthMode.SIGN_UP }.padding(8.dp)
                        )
                    }
                    if (mode != AuthMode.FORGOT) {
                        Text(
                            "Forgot?",
                            color = ActiveGrey,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { mode = AuthMode.FORGOT }.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInForm(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    StyledTextField("License / Email", email) { email = it }
    StyledTextField("Terminal Password", password, isPassword = true) { password = it }
    
    if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(errorMessage ?: "", color = com.novahost.app.ui.theme.Crimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(16.dp))
    GradientButton(
        text = if (isChecking) "AUTHORIZING..." else "INITIALIZE LINK",
        onClick = { 
            if (email.isNotBlank() && password.isNotBlank() && !isChecking) {
                isChecking = true
                errorMessage = null
                scope.launch {
                    try {
                        com.novahost.app.sdk.NovaHostBackend.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                        // Optionally fetch avatar_url
                        try {
                            val profile = com.novahost.app.sdk.NovaHostBackend.client.postgrest.from("profiles")
                                .select().decodeSingleOrNull<UserProfile>()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) // pop everything
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Authorization Failed."
                    } finally {
                        isChecking = false
                    }
                }
            } 
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SignUpForm() {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    StyledTextField("Agent Name", name) { name = it }
    StyledTextField("Authorization Email", email) { email = it }
    StyledTextField("Secret Pin", password, isPassword = true) { password = it }

    if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(errorMessage ?: "", color = com.novahost.app.ui.theme.Crimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    if (successMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(successMessage ?: "", color = com.novahost.app.ui.theme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(16.dp))
    GradientButton(
        text = if (isChecking) "REGISTERING..." else "CREATE PROFILE",
        onClick = {
            if (email.isNotBlank() && password.isNotBlank() && name.isNotBlank() && !isChecking) {
                isChecking = true
                errorMessage = null
                successMessage = null
                scope.launch {
                    try {
                        com.novahost.app.sdk.NovaHostBackend.client.auth.signUpWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                            this.email = email
                            this.password = password
                        }
                        successMessage = "Profile registered successfully. Please check your email to verify."
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Registration Failed: ${e.message}"
                    } finally {
                        isChecking = false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ForgotForm() {
    var email by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    StyledTextField("Recovery Node (Email)", email) { email = it }

    if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(errorMessage ?: "", color = com.novahost.app.ui.theme.Crimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    if (successMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(successMessage ?: "", color = com.novahost.app.ui.theme.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(16.dp))
    GradientButton(
        text = if (isChecking) "TRANSMITTING..." else "TRANSMIT RECOVERY",
        onClick = {
            if (email.isNotBlank() && !isChecking) {
                isChecking = true
                errorMessage = null
                successMessage = null
                scope.launch {
                    try {
                        com.novahost.app.sdk.NovaHostBackend.client.auth.resetPasswordForEmail(email)
                        successMessage = "Recovery instructions sent. Check your email."
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Transmission Failed: ${e.message}"
                    } finally {
                        isChecking = false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StyledTextField(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit
) {
    val themeState = LocalNovaHostTheme.current
    val primaryColor = themeState.primaryColor

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = ActiveGrey) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            cursorColor = primaryColor,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.05f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
