package com.ncs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Chip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.ncs.model.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = project.title, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        text = "${project.contentType.name} • ${project.platform.displayName}",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(project.updatedAt)),
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusChip(status = project.status)
                project.rating?.let { rating ->
                    RatingChip(rating = rating)
                }
                project.isTemplate.let { isTemplate ->
                    if (isTemplate) TemplateChip()
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: Project.Status) {
    val (label, color) = when (status) {
        Project.Status.DRAFT -> "Draft" to androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
        Project.Status.RESEARCHING -> "Riset" to Color.Blue
        Project.Status.GENERATING_ASSETS -> "Generate" to Color.Orange
        Project.Status.EDITING -> "Edit" to Color.Green
        Project.Status.RENDERING -> "Render" to Color.Magenta
        Project.Status.COMPLETED -> "Selesai" to Color.Green
        Project.Status.FAILED -> "Gagal" to Color.Red
    }
    Chip(
        onClick = { },
        modifier = Modifier.padding(4.dp),
        colors = androidx.compose.material3.ChipDefaults.colors(containerColor = color.copy(alpha = 0.2f))
    ) {
        Text(text = label, fontSize = 10.sp, color = color)
    }
}

@Composable
fun RatingChip(rating: Int) {
    Chip(
        onClick = { },
        modifier = Modifier.padding(4.dp),
        colors = androidx.compose.material3.ChipDefaults.colors(containerColor = Color.Yellow.copy(alpha = 0.3f))
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material.Icon(androidx.compose.material.Icons.Default.Star, "Star", tint = Color.Yellow)
            Text(text = rating.toString(), fontSize = 10.sp, color = Color.Yellow.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun TemplateChip() {
    Chip(
        onClick = { },
        modifier = Modifier.padding(4.dp),
        colors = androidx.compose.material3.ChipDefaults.colors(containerColor = Color.Cyan.copy(alpha = 0.2f))
    ) {
        Text(text = "Template", fontSize = 10.sp, color = Color.Cyan)
    }
}
