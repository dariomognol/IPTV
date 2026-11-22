package com.example.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DarkBackgroundLogin = Color(0xFF0D131D)
val CardBackgroundLogin = Color(0xFF161E2E)
val AccentBlue = Color(0xFF00C6FF)
val InputFieldBackground = Color(0xFF222C3D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: (() -> Unit)? = null,
    onLoginSuccess: (UserAccount) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://") }

    var errorMessage by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackgroundLogin)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBackgroundLogin)
                .padding(28.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onBack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .background(if (isBackFocused) AccentBlue.copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                            .border(if (isBackFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Intestazione
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(46.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AGGIUNGI UTENTE XTREAM",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Inserisci i dati della tua lista Xtream Codes",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Campo 1: Nome Lista
            TvOutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = "Nome Playlist (es. Mia TV)",
                leadingIcon = Icons.Default.Bookmark
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Campo 2: Username
            TvOutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = "Username",
                leadingIcon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Campo 3: Password
            TvOutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Campo 4: Server URL
            TvOutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = "URL Server (es. http://server.tv:8080)",
                leadingIcon = Icons.Default.Link
            )

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pulsante CONFERMA E AGGIUNGI UTENTE
            LoginButton(
                text = "CONFERMA E AGGIUNGI UTENTE",
                onClick = {
                    if (username.isBlank() || password.isBlank() || serverUrl.isBlank()) {
                        errorMessage = "Compila tutti i campi obbligatori (Username, Password, Server URL)"
                    } else {
                        val account = UserAccount(
                            playlistName = if (playlistName.isBlank()) "Utente IPTV" else playlistName,
                            username = username.trim(),
                            password = password.trim(),
                            serverUrl = serverUrl.trim()
                        )
                        onLoginSuccess(account)
                    }
                }
            )
        }
    }
}

@Composable
fun TvOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray, fontSize = 12.sp) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = AccentBlue) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = InputFieldBackground,
            unfocusedContainerColor = InputFieldBackground,
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun LoginButton(
    text: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color(0xFF00E5FF) else AccentBlue,
            contentColor = if (isFocused) Color.Black else Color.White
        ),
        border = if (isFocused) BorderStroke(2.dp, Color.White) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
