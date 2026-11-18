package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BikeCatalog
import com.example.data.BikeSpec
import com.example.data.ControlSettings
import com.example.data.TrackMode
import com.example.ui.GarageUiState
import com.example.ui.theme.ApexCrimson
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.GarageSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.PitSlate
import com.example.ui.theme.SectorGreen
import com.example.ui.theme.ShiftAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TitaniumSilver

@Composable
fun HomeGarageScreen(
    uiState: GarageUiState,
    onSelectBike: (String) -> Unit,
    onSelectTrack: (TrackMode) -> Unit,
    onUpdateSettings: (ControlSettings) -> Unit,
    onStartRace: () -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonObsidian)
    ) {
        // Photorealistic Superbike Garage Backdrop
        Image(
            painter = painterResource(id = R.drawable.img_garage_hero),
            contentDescription = "Superbike pit garage backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Cinematic dark gradient scrim for crisp HUD contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD9090C10),
                            Color(0x99090C10),
                            Color(0xE6090C10),
                            Color(0xFA090C10)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val isWide = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ApexCrimson)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "300 KM/H",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "APEX 300",
                                style = MaterialTheme.typography.headlineLarge,
                                color = TitaniumSilver
                            )
                        }
                        Text(
                            text = stringResource(R.string.tagline_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSteel
                        )
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(GarageSurface.copy(alpha = 0.85f))
                            .border(1.dp, HairlineBorder, CircleShape)
                            .testTag("garage_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Control Settings",
                            tint = TitaniumSilver
                        )
                    }
                }

                // Personal Telemetry Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RecordChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = "PEAK SPEED",
                        value = "${maxOf(uiState.records.bestTopSpeedKmh, 300)} KM/H",
                        accent = TelemetryCyan
                    )
                    RecordChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.EmojiEvents,
                        label = "HIGH SCORE",
                        value = if (uiState.records.bestScore > 0) "${uiState.records.bestScore}" else "—",
                        accent = ShiftAmber
                    )
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionHeader(title = "SELECT SUPERBIKE", subtitle = "1000cc • Tuned to 300 KM/H")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(BikeCatalog.bikes, key = { it.id }) { bike ->
                                    BikeSpecCard(
                                        bike = bike,
                                        selected = bike.id == uiState.selectedBike.id,
                                        onClick = { onSelectBike(bike.id) }
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionHeader(title = "SELECT CIRCUIT", subtitle = "Choose environment & race rules")
                            TrackModeSelector(
                                selected = uiState.selectedTrackMode,
                                onSelect = onSelectTrack
                            )
                        }
                    }
                } else {
                    SectionHeader(title = "SELECT SUPERBIKE", subtitle = "1000cc • Tuned to 300 KM/H")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(BikeCatalog.bikes, key = { it.id }) { bike ->
                            BikeSpecCard(
                                bike = bike,
                                selected = bike.id == uiState.selectedBike.id,
                                onClick = { onSelectBike(bike.id) }
                            )
                        }
                    }

                    SectionHeader(title = "SELECT CIRCUIT", subtitle = "Choose environment & race rules")
                    TrackModeSelector(
                        selected = uiState.selectedTrackMode,
                        onSelect = onSelectTrack
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Launch CTA
                Button(
                    onClick = onStartRace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("race_now_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ApexCrimson,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_race_now),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            letterSpacing = 1.4.sp
                        )
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = uiState.settings,
            onUpdate = onUpdateSettings,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = TitaniumSilver
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedSteel,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RecordChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        color = PitSlate.copy(alpha = 0.88f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel,
                    fontSize = 10.sp
                )
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TitaniumSilver
                )
            }
        }
    }
}

@Composable
private fun BikeSpecCard(
    bike: BikeSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) bike.primaryLivery else HairlineBorder
    val bg = if (selected) ElevatedCard.copy(alpha = 0.94f) else PitSlate.copy(alpha = 0.85f)

    Surface(
        modifier = Modifier
            .width(248.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("bike_card_${bike.id}"),
        color = bg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(bike.primaryLivery.copy(alpha = 0.22f))
                            .border(1.dp, bike.primaryLivery, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TwoWheeler,
                            contentDescription = bike.name,
                            tint = bike.accentGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = bike.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TitaniumSilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = bike.engineLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel,
                            fontSize = 11.sp
                        )
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bike.primaryLivery)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "READY",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniSpecBadge("TOP SPEED", "300 KM/H", TelemetryCyan)
                MiniSpecBadge("POWER", "${bike.horsepower} HP", ShiftAmber)
                MiniSpecBadge("DRY WT", "${bike.weightKg} KG", TitaniumSilver)
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StatBar(label = "ACCEL", value = bike.accelRating, color = bike.accentGlow)
                StatBar(label = "GRIP", value = bike.handlingRating, color = TelemetryCyan)
                StatBar(label = "BRAKE", value = bike.brakingRating, color = SectorGreen)
            }
        }
    }
}

@Composable
private fun MiniSpecBadge(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = MutedSteel
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
private fun StatBar(label: String, value: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = MutedSteel,
            modifier = Modifier.width(42.dp)
        )
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color(0xFF283344)
        )
    }
}

@Composable
private fun TrackModeSelector(
    selected: TrackMode,
    onSelect: (TrackMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TrackCard(
            mode = TrackMode.SUNSET_HIGHWAY,
            drawableRes = R.drawable.img_sky_sunset,
            icon = Icons.Outlined.WbSunny,
            badgeText = "ENDLESS BLITZ",
            badgeColor = ShiftAmber,
            selected = selected == TrackMode.SUNSET_HIGHWAY,
            onClick = { onSelect(TrackMode.SUNSET_HIGHWAY) }
        )
        TrackCard(
            mode = TrackMode.NIGHT_CIRCUIT,
            drawableRes = R.drawable.img_sky_night,
            icon = Icons.Outlined.Nightlight,
            badgeText = "3-LAP GP RACE",
            badgeColor = TelemetryCyan,
            selected = selected == TrackMode.NIGHT_CIRCUIT,
            onClick = { onSelect(TrackMode.NIGHT_CIRCUIT) }
        )
    }
}

@Composable
private fun TrackCard(
    mode: TrackMode,
    drawableRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String,
    badgeColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("track_card_${mode.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) badgeColor else HairlineBorder),
        color = PitSlate
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = mode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xEA090C10), Color(0xB3090C10), Color(0x55090C10))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = badgeText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = badgeColor
                        )
                    }
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TitaniumSilver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Selected Track",
                            tint = CarbonObsidian,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    settings: ControlSettings,
    onUpdate: (ControlSettings) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PitSlate,
        title = {
            Text(
                text = "RIDER & TELEMETRY SETTINGS",
                style = MaterialTheme.typography.titleMedium,
                color = TitaniumSilver
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggleRow(
                    title = stringResource(R.string.setting_tilt_steer),
                    subtitle = "Lean device left/right to steer bike",
                    checked = settings.tiltSteerEnabled,
                    onCheckedChange = { onUpdate(settings.copy(tiltSteerEnabled = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_auto_gas),
                    subtitle = "Automatic throttle up to 300 KM/H",
                    checked = settings.autoThrottleEnabled,
                    onCheckedChange = { onUpdate(settings.copy(autoThrottleEnabled = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_engine_audio),
                    subtitle = "Live 14,500 RPM exhaust sound",
                    checked = settings.soundEnabled,
                    onCheckedChange = { onUpdate(settings.copy(soundEnabled = it)) }
                )
                SettingToggleRow(
                    title = stringResource(R.string.setting_haptics),
                    subtitle = "Curb rumble, upshift & near-miss vibration",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { onUpdate(settings.copy(hapticsEnabled = it)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("DONE", color = TelemetryCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TitaniumSilver)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedSteel, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CarbonObsidian,
                checkedTrackColor = TelemetryCyan
            )
        )
    }
}
