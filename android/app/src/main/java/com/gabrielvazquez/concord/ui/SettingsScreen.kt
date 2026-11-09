package com.gabrielvazquez.concord.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gabrielvazquez.concord.BuildConfig
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.STATUS_LABEL

@Composable
fun SettingsScreen() {
    val c = Concord.I
    val me = c.me ?: return
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(me.name) }
    var status by remember { mutableStateOf(me.statusText ?: "") }
    var pronouns by remember { mutableStateOf(me.pronouns ?: "") }
    var username by remember { mutableStateOf(me.username ?: "") }
    var bio by remember { mutableStateOf(me.bio ?: "") }
    var busy by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) c.uploadAvatar(uri) }
    Dialog(onDismissRequest = { Ui.settingsOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        if (look) AppearanceScreen { look = false }
        Column(Modifier.fillMaxSize().background(C.bg).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Ui.settingsOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Text("Configurações", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Section("Meu perfil") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Avatar(me.id, 76.dp)
                            Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column { StyledName(me.id, c.name(me.id), 18.sp, FontWeight.ExtraBold); Text(STATUS_LABEL[c.myStatus()] ?: "", color = statusColor(c.myStatus()), fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(14.dp))
                    field(name, { name = it.take(24) }, "Nome de exibição"); Spacer(Modifier.height(10.dp))
                    field(username, { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '_' }.take(20) }, "Nome de usuário (@) · é como os amigos te acham"); Spacer(Modifier.height(10.dp))
                    field(pronouns, { pronouns = it.take(24) }, "Pronomes"); Spacer(Modifier.height(10.dp))
                    field(status, { status = it.take(80) }, "Status personalizado"); Spacer(Modifier.height(10.dp))
                    field(bio, { bio = it.take(300) }, "Sobre mim"); Spacer(Modifier.height(14.dp))
                    PrimaryButton("Salvar", busy) { busy = true; c.saveProfile(name, status, bio, pronouns, username) { busy = false } }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, C.line2, RoundedCornerShape(14.dp)).clickable { look = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = C.tx2); Spacer(Modifier.width(12.dp)); Text("Aparência: nome, moldura, banner e premium", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                }
                Section("Status") {
                    STATUS_LABEL.forEach { (k, l) ->
                        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (me.presence == k) C.card2 else Color.Transparent).clickable { c.setStatus(k) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(k))); Spacer(Modifier.width(12.dp)); Text(l, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Section("App") {
                    Item(Icons.Default.Notifications, "Notificações do Android") {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                    Item(Icons.Default.SystemUpdate, if (com.gabrielvazquez.concord.Updater.available != null) "Atualizar para ${com.gabrielvazquez.concord.Updater.available}" else "Procurar atualização") {
                        val act = ctx as? android.app.Activity
                        if (com.gabrielvazquez.concord.Updater.available != null && act != null) com.gabrielvazquez.concord.Updater.install(act, c.scope) { c.say(it) }
                        else com.gabrielvazquez.concord.Updater.check(c, force = true) { if (!it) c.say("Você já está na versão mais nova.") }
                    }
                    Item(Icons.AutoMirrored.Filled.Logout, "Trocar de conta") { Ui.settingsOpen = false; c.logout() }
                    Text("CONCORD para Android · versão ${BuildConfig.VERSION_NAME}", color = C.tx3, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.panel).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(12.dp)); content()
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.tx2); Spacer(Modifier.width(14.dp)); Text(label, fontWeight = FontWeight.SemiBold)
    }
}
