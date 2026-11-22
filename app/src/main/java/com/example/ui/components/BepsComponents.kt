package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SchoolConfig
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.ui.theme.BepsCrimson
import com.example.ui.theme.BepsCrimsonContainer
import com.example.ui.theme.BepsEmerald
import com.example.ui.theme.BepsEmeraldContainer
import com.example.ui.theme.BepsGold
import com.example.ui.theme.BepsGoldContainer
import com.example.ui.theme.BepsTealContainer
import com.example.ui.theme.BepsTealInfo
import com.example.util.QrAndPdfUtils

@Composable
fun BepsCrestBadge(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color(0xFFF7F7F7),
        shadowElevation = 2.dp
    ) {
        Image(
            painter = painterResource(id = R.drawable.beps_logo),
            contentDescription = stringResource(id = R.string.beps_crest_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
fun StudentAvatar(
    fullName: String,
    photoKey: String,
    status: StudentStatus,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val ringColor = when (status) {
        StudentStatus.NOT_ARRIVED -> MaterialTheme.colorScheme.outline
        StudentStatus.ARRIVED_AT_GATE -> BepsTealInfo
        StudentStatus.IN_CLASSROOM -> BepsEmerald
        StudentStatus.PICKUP_REQUESTED -> BepsGold
        StudentStatus.READY_FOR_PICKUP -> BepsCrimson
        StudentStatus.COLLECTED, StudentStatus.BOARDED_BUS -> BepsEmerald
    }
    val bgTint = when {
        photoKey.contains("girl") -> Color(0xFFE6F4EE)
        photoKey.contains("boy") -> Color(0xFFE7F0FA)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val initials = fullName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "BP" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgTint)
            .border(2.5.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ringColor
        )
    }
}

@Composable
fun StatusBadge(
    status: StudentStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (status) {
        StudentStatus.NOT_ARRIVED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Schedule
        )
        StudentStatus.ARRIVED_AT_GATE -> Triple(
            BepsTealContainer,
            BepsTealInfo,
            Icons.Default.VerifiedUser
        )
        StudentStatus.IN_CLASSROOM -> Triple(
            BepsEmeraldContainer,
            BepsEmerald,
            Icons.Default.School
        )
        StudentStatus.PICKUP_REQUESTED -> Triple(
            BepsGoldContainer,
            Color(0xFF6B4E00),
            Icons.Default.NotificationsActive
        )
        StudentStatus.READY_FOR_PICKUP -> Triple(
            BepsCrimsonContainer,
            BepsCrimson,
            Icons.Default.VerifiedUser
        )
        StudentStatus.COLLECTED -> Triple(
            BepsEmeraldContainer,
            BepsEmerald,
            Icons.Default.CheckCircle
        )
        StudentStatus.BOARDED_BUS -> Triple(
            BepsTealContainer,
            BepsTealInfo,
            Icons.Default.DirectionsBus
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TimelineStepBar(
    status: StudentStatus,
    modifier: Modifier = Modifier
) {
    val labels = listOf("Gate", "Class", "Request", "Ready", "Handover")
    val activeStep = status.stepIndex
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { idx, label ->
            val stepNum = idx + 1
            val reached = activeStep >= stepNum
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (reached) BepsEmerald
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) BepsEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DispersalWindowBanner(
    config: SchoolConfig?,
    modifier: Modifier = Modifier
) {
    val open = config?.dispersalOpen == true
    val bg = if (open) BepsEmeraldContainer else BepsCrimsonContainer
    val fg = if (open) BepsEmerald else BepsCrimson
    val icon = if (open) Icons.Default.LockOpen else Icons.Default.Lock
    val statusText = if (open) "Dispersal Window OPEN" else "Dispersal Window LOCKED"
    val windowLabel = config?.dispersalWindowLabel?.ifBlank { "01:00 PM - 02:15 PM" } ?: "01:00 PM - 02:15 PM"
    val notice = config?.activeNotice?.ifBlank {
        if (open) "Parents may request child pickup. Teacher & Security verification required."
        else "Pickup requests are disabled until authorized dismissal begins."
    } ?: "Connecting to school dispersal schedule…"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = fg.copy(alpha = 0.14f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = statusText,
                        tint = fg,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        color = fg,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = windowLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun QrPassDialog(
    student: Student,
    onDismiss: () -> Unit
) {
    val qrBitmap: Bitmap? = remember(student.studentCode) {
        QrAndPdfUtils.generateQrBitmap(student.qrPayload(), 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = BepsEmerald)
                Text("BEPS Student Gate Pass", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = student.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${student.grade} • ${student.studentCode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (qrBitmap != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                        tonalElevation = 2.dp,
                        shadowElevation = 3.dp
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Pass for ${student.fullName}",
                            modifier = Modifier
                                .size(210.dp)
                                .padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Present this QR pass at BEPS Ras Al Khaimah arrival & collection gates.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_qr_dialog_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.School,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
