package com.example.iptv

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val DarkBackground = Color(0xFF0D131E)
val HeaderBackground = Color(0xFF161D2B)

val LiveTvGradient = Brush.verticalGradient(
    listOf(Color(0xFF00E6A2), Color(0xFF2979FF), Color(0xFF651FFF))
)

val MoviesGradient = Brush.horizontalGradient(
    listOf(Color(0xFFE0115F), Color(0xFFFF5226), Color(0xFFFF9E1B))
)

val SeriesGradient = Brush.horizontalGradient(
    listOf(Color(0xFF8B1FA9), Color(0xFF4A4DE6), Color(0xFF3CA2F4))
)

@Composable
fun SmartersDashboardScreen(
    appName: String = "IPTV PRO",
    accountName: String = "User1234",
    accountsList: List<String> = listOf(),
    timezoneId: String = "Europe/Rome",
    onSelectAccount: (String) -> Unit = {},
    onRemoveAccount: (String) -> Unit = {},
    onAddNewAccount: () -> Unit = {},
    onNavigateToLiveTv: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onRefreshData: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAccountDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. HEADER
            TopDashboardHeader(
                appName = appName,
                timezoneId = timezoneId,
                onAccountClick = { showAccountDialog = true },
                onNavigateToSettings = onNavigateToSettings,
                onRefreshData = onRefreshData,
                onExitApp = onExitApp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. CARDS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LiveTvActionCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToLiveTv
                )

                DashboardCenteredCard(
                    title = "MOVIES",
                    icon = Icons.Default.PlayCircle,
                    gradient = MoviesGradient,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMovies
                )

                DashboardCenteredCard(
                    title = "SERIES",
                    icon = Icons.Outlined.Movie,
                    gradient = SeriesGradient,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSeries
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. FOOTER
            AccountFooterInfo(accountName = accountName)
        }

        // --- DIALOG GESTIONE ACCOUNT ---
        if (showAccountDialog) {
            AccountSelectionDialog(
                currentAccount = accountName,
                accounts = accountsList.ifEmpty { listOf(accountName) },
                onSelectAccount = { selectedUser ->
                    showAccountDialog = false
                    onSelectAccount(selectedUser)
                    Toast.makeText(context, "Profilo attivo: $selectedUser", Toast.LENGTH_SHORT).show()
                },
                onRemoveAccount = { userToRemove ->
                    onRemoveAccount(userToRemove)
                    if (accountsList.size <= 1) {
                        showAccountDialog = false
                    } else {
                        Toast.makeText(context, "Profilo $userToRemove rimosso", Toast.LENGTH_SHORT).show()
                    }
                },
                onAddNewAccount = {
                    showAccountDialog = false
                    onAddNewAccount()
                },
                onDismiss = { showAccountDialog = false }
            )
        }
    }
}

@Composable
fun AccountSelectionDialog(
    currentAccount: String,
    accounts: List<String>,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddNewAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161D2B),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gestione Utenti",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                var isCloseFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .background(if (isCloseFocused) Color(0xFF00C6FF).copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                        .border(if (isCloseFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Chiudi", tint = if (isCloseFocused) Color.White else Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "Seleziona o rimuovi un profilo:", color = Color.LightGray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))

                accounts.forEach { account ->
                    val isCurrent = account == currentAccount
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { onSelectAccount(account) },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isFocused -> Color(0xFF1E4175)
                            isCurrent -> Color(0xFF00C6FF).copy(alpha = 0.15f)
                            else -> Color(0xFF222C3D)
                        },
                        border = BorderStroke(
                            width = if (isFocused) 3.dp else if (isCurrent) 2.dp else 1.dp,
                            color = if (isFocused) Color(0xFFFFC107) else if (isCurrent) Color(0xFF00C6FF) else Color(0xFF2A364F)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = if (isFocused) Color(0xFFFFC107) else if (isCurrent) Color(0xFF00C6FF) else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = account,
                                    color = if (isFocused) Color(0xFFFFC107) else Color.White,
                                    fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 16.sp
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(color = Color(0xFF00C6FF), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = "ATTIVO",
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            var isDeleteFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { onRemoveAccount(account) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .onFocusChanged { isDeleteFocused = it.isFocused }
                                    .background(if (isDeleteFocused) Color(0xFFFF5252).copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                    .border(if (isDeleteFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Rimuovi Utente",
                                    tint = if (isDeleteFocused) Color.White else Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                var isAddFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = onAddNewAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isAddFocused = it.isFocused },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(if (isAddFocused) 2.dp else 1.dp, if (isAddFocused) Color(0xFFFFC107) else Color(0xFF00C6FF)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isAddFocused) Color(0xFF1E4175) else Color.Transparent,
                        contentColor = if (isAddFocused) Color(0xFFFFC107) else Color(0xFF00C6FF)
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Aggiungi Nuovo Utente", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            var isDisconnectFocused by remember { mutableStateOf(false) }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B4B)),
                onClick = { onRemoveAccount(currentAccount) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .onFocusChanged { isDisconnectFocused = it.isFocused }
                    .border(if (isDisconnectFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(8.dp))
            ) {
                Text(text = "Disconnetti Attivo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            var isCancelFocused by remember { mutableStateOf(false) }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .onFocusChanged { isCancelFocused = it.isFocused }
                    .background(if (isCancelFocused) Color(0xFF2A364F) else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(if (isCancelFocused) BorderStroke(1.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(6.dp))
            ) {
                Text(text = "Annulla", color = Color.White)
            }
        }
    )
}

@Composable
fun TopDashboardHeader(
    appName: String = "NOVA STREAM",
    timezoneId: String = "Europe/Rome",
    onAccountClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onRefreshData: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTime by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "RefreshRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    LaunchedEffect(timezoneId) {
        val sdf = SimpleDateFormat("HH:mm - dd MMM yyyy", Locale.ITALIAN)
        sdf.timeZone = TimeZone.getTimeZone(timezoneId)
        while (true) {
            currentTime = sdf.format(Date())
            delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HeaderBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = appName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = currentTime, color = Color.LightGray, fontSize = 14.sp)

            HeaderIconButton(
                icon = Icons.Default.Refresh,
                rotation = if (isRefreshing) rotationAngle else 0f,
                onClick = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        Toast.makeText(context, "Aggiornamento dati...", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            onRefreshData()
                            delay(2000)
                            isRefreshing = false
                            Toast.makeText(context, "Dati aggiornati!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            HeaderIconButton(icon = Icons.Default.Notifications, onClick = { })
            HeaderIconButton(icon = Icons.Default.Settings, onClick = onNavigateToSettings)
            HeaderIconButton(icon = Icons.Default.AccountCircle, onClick = onAccountClick)
            HeaderIconButton(icon = Icons.Default.PowerSettingsNew, onClick = onExitApp)
        }
    }
}

@Composable
fun DashboardCenteredCard(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { isFocused = it.isFocused },
        border = if (isFocused) BorderStroke(3.dp, Color.White) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(gradient).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(68.dp))
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.2.sp)
            }
        }
    }
}

@Composable
fun LiveTvActionCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { isFocused = it.isFocused },
        border = if (isFocused) BorderStroke(3.dp, Color.White) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(LiveTvGradient).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LiveTvCustomIcon()
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "LIVE TV", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.2.sp)
            }
        }
    }
}

@Composable
fun LiveTvCustomIcon() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(width = 64.dp, height = 22.dp)) {
            val cx = size.width / 2
            val cy = size.height

            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - 10.dp.toPx(), cy - 10.dp.toPx()),
                size = Size(20.dp.toPx(), 20.dp.toPx())
            )

            drawLine(
                color = Color.White,
                start = Offset(cx - 5.dp.toPx(), cy - 6.dp.toPx()),
                end = Offset(cx - 24.dp.toPx(), 4.dp.toPx()),
                strokeWidth = 3.5f.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx - 24.dp.toPx(), 4.dp.toPx()))

            drawLine(
                color = Color.White,
                start = Offset(cx + 5.dp.toPx(), cy - 6.dp.toPx()),
                end = Offset(cx + 24.dp.toPx(), 4.dp.toPx()),
                strokeWidth = 3.5f.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx + 24.dp.toPx(), 4.dp.toPx()))
        }

        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(4.dp, Color.White),
            modifier = Modifier.width(110.dp).height(72.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(text = "LIVE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp)
                Text(text = "TV", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun HeaderIconButton(
    icon: ImageVector,
    rotation: Float = 0f,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isFocused) Color(0xFF00C6FF) else Color(0xFF222C3D),
        border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
        modifier = Modifier.size(42.dp).onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(22.dp).rotate(rotation)
            )
        }
    }
}

@Composable
fun AccountFooterInfo(accountName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Utente: $accountName", color = Color.Gray, fontSize = 13.sp)
        Text(text = "Scadenza Abbonamento: Illimitata", color = Color.Gray, fontSize = 13.sp)
    }
}