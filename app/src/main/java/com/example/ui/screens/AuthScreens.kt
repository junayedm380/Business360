package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AppLanguage
import com.example.domain.AppStrings

@Composable
fun InitialSuperAdminSetupScreen(
    strings: AppStrings,
    language: AppLanguage,
    errorMessage: String?,
    onLanguageChange: (AppLanguage) -> Unit,
    onCreateSuperAdmin: (businessName: String, fullName: String, email: String, password: String) -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AuthShell(
        heroTitle = strings.setupSuperAdminTitle,
        heroSubtitle = strings.setupSuperAdminSubtitle,
        language = language,
        onLanguageChange = onLanguageChange
    ) {
        OutlinedTextField(
            value = businessName,
            onValueChange = { businessName = it },
            label = { Text(strings.businessNameLabel) },
            leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_business_input")
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text(strings.fullNameLabel) },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_name_input")
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(strings.emailLabel) },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_email_input")
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(strings.passwordLabel) },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Toggle password")
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("setup_password_input")
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onCreateSuperAdmin(businessName, fullName, email, password) },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_super_admin_button")
        ) {
            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text(strings.createSuperAdminBtn)
        }
    }
}

@Composable
fun LoginScreen(
    strings: AppStrings,
    language: AppLanguage,
    errorMessage: String?,
    onLanguageChange: (AppLanguage) -> Unit,
    onLogin: (identifier: String, password: String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AuthShell(
        heroTitle = strings.signInTitle,
        heroSubtitle = strings.signInSubtitle,
        language = language,
        onLanguageChange = onLanguageChange
    ) {
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text(strings.emailLabel) },
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_email_input")
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(strings.passwordLabel) },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Toggle password")
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_password_input")
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onLogin(identifier, password) },
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_submit_button")
        ) {
            Text(strings.signInBtn)
        }
    }
}

@Composable
private fun AuthShell(
    heroTitle: String,
    heroSubtitle: String,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    formContent: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = "Language", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.padding(4.dp))
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.label) },
                            modifier = Modifier.padding(start = 6.dp).testTag("lang_chip_${lang.code}")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.erp_hero_banner_1795274162661),
                        contentDescription = "CraftERP Industrial Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.18f), Color(0xFF0F2942).copy(alpha = 0.88f))
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.business360_logo),
                                contentDescription = "Business360 Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = heroTitle,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = heroSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        formContent()
                    }
                }
            }
        }
    }
}
