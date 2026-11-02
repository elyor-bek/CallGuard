package uz.callguard.app.ui

import uz.callguard.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onRequestRole: () -> Unit,
    roleAvailable: Boolean,
    requestingRole: Boolean,
    notificationsGranted: Boolean,
    notificationsDenied: Boolean,
    onRequestNotifications: () -> Unit,
    overlayGranted: Boolean,
    onRequestOverlay: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.padding(28.dp).size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(R.string.welcome_hint), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.overlay_permission_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (overlayGranted) R.string.overlay_permission_granted else R.string.overlay_permission_hint))
                    if (!overlayGranted) OutlinedButton(onClick = onRequestOverlay) {
                        Text(stringResource(R.string.overlay_permission_allow))
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.notification_intro), style = MaterialTheme.typography.titleLarge)
                    Text(if (notificationsGranted) stringResource(R.string.notification_allowed)
                        else stringResource(R.string.notification_intro_hint))
                    if (!notificationsGranted) OutlinedButton(onClick = onRequestNotifications, enabled = !requestingRole) {
                        Text(if (notificationsDenied) stringResource(R.string.notification_settings) else stringResource(R.string.notification_allow))
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.spam_protection), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.role_intro),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.role_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onRequestRole, enabled = roleAvailable && !requestingRole,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (requestingRole) stringResource(R.string.permission_wait) else stringResource(R.string.enable_protection))
                    }
                    if (!roleAvailable) Text(stringResource(R.string.role_unavailable),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    if (errorMessage != null) Text(errorMessage, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text(stringResource(R.string.permission_hint),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
