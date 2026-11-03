package com.gabrielvazquez.concord.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.Msg

@Composable
fun ChatPane(key: String, placeholder: String, readonly: Boolean) {
    val c = Concord.I
    val list = rememberLazyListState()
    val size = c.messages.size
    LaunchedEffect(key, size) { if (size > 0) list.animateScrollToItem(size - 1) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) c.sendImage(uri) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (c.loadingMsgs && c.messages.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = C.tx3, strokeWidth = 2.dp)
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                item(key = "welcome-$key") {
                    if (!c.loadingMsgs) Column(Modifier.padding(16.dp)) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.Tag, null, tint = Color.Black) }
                        Text("Bem-vindo(a) a $placeholder", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                itemsIndexed(c.messages, key = { _, m -> m.id }) { i, m ->
                    val prev = c.messages.getOrNull(i - 1)
                    if (prev == null || dayKey(prev.at) != dayKey(m.at)) DaySep(m.at)
                    val grouped = prev != null && prev.userId == m.userId && m.at - prev.at < 5 * 60_000 && dayKey(prev.at) == dayKey(m.at)
                    MessageRow(m, grouped)
                }
            }
        }
        val typers = c.typing.values.filter { it.second > System.currentTimeMillis() && c.lastTypingCh == key }.map { it.first }
        if (typers.isNotEmpty()) Text(if (typers.size == 1) "${typers[0]} está digitando…" else "${typers.take(3).joinToString(", ")} estão digitando…", color = C.tx3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Composer(placeholder, readonly, onAttach = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    }
}

@Composable
private fun DaySep(t: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = C.line); Text(timeLabel(t).substringBefore(" às").let { if (it.length > 10) it.take(10) else it }, color = C.tx3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp)); HorizontalDivider(Modifier.weight(1f), color = C.line)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(m: Msg, grouped: Boolean) {
    val c = Concord.I
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val mine = m.userId == c.meId
    val text = rich(m.content, c.me?.name)
    val mentioned = !mine && c.me?.name?.let { m.content.contains("@$it", true) } == true
    Box {
        Row(Modifier.fillMaxWidth().background(if (mentioned) Color(0x14FBBF24) else Color.Transparent)
            .combinedClickable(onClick = {}, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
            .padding(start = 14.dp, end = 14.dp, top = if (grouped) 1.dp else 10.dp, bottom = 1.dp)) {
            if (grouped) Spacer(Modifier.width(48.dp))
            else { Box(Modifier.clickable { m.userId?.let { Ui.profile = it } }) { Avatar(m.userId, 38.dp, fallbackName = m.author, fallbackColor = m.color) }; Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                if (!grouped) Row(verticalAlignment = Alignment.CenterVertically) {
                    val p = m.userId?.let { c.profiles[it] }
                    Text(c.name(m.userId, m.author), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = p?.nameColor?.let { parseColor(it) } ?: C.tx, modifier = Modifier.clickable { m.userId?.let { Ui.profile = it } })
                    if (m.userId != null && c.admins.contains(m.userId)) { Spacer(Modifier.width(6.dp)); Text("ADM", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(4.dp, 1.dp)) }
                    Spacer(Modifier.width(8.dp)); Text(timeLabel(m.at), color = C.tx3, fontSize = 11.sp)
                }
                if (isImageUrl(m.content)) AsyncImage(m.content.trim(), null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.padding(top = 4.dp).widthIn(max = 280.dp).heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)).clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.content.trim()))) })
                else ClickableText(text, style = TextStyle(color = Color(0xFFD4D4D8), fontSize = 15.sp, lineHeight = 21.sp)) { off ->
                    text.getStringAnnotations("url", off, off).firstOrNull()?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item))) }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = C.card) {
            DropdownMenuItem(text = { Text("Copiar texto") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { clip.setText(AnnotatedString(m.content)); menu = false })
            if (c.isAdmin && c.view is com.gabrielvazquez.concord.View.Text) DropdownMenuItem(text = { Text("Excluir mensagem", color = C.red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = C.red) }, onClick = { c.deleteMessage(m); menu = false })
        }
    }
}


@Composable
private fun Composer(placeholder: String, readonly: Boolean, onAttach: () -> Unit) {
    val c = Concord.I
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp).clip(RoundedCornerShape(22.dp)).background(C.card), verticalAlignment = Alignment.Bottom) {
        IconButton(onClick = onAttach, enabled = !readonly) { Icon(Icons.Default.AddPhotoAlternate, "Enviar imagem", tint = C.tx2) }
        TextField(value = text, onValueChange = { text = it.take(2000); c.typingNow() }, enabled = !readonly,
            placeholder = { Text(if (readonly) "Somente leitura" else "Conversar em $placeholder", color = C.tx3) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 160.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent, cursorColor = Color.White))
        val can = text.isNotBlank() && !readonly
        Box(Modifier.padding(6.dp).size(38.dp).clip(CircleShape).background(if (can) Color.White else C.card2)
            .then(if (can) Modifier.clickable { val t = text; text = ""; c.send(t) { text = it } } else Modifier), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (can) Color.Black else C.tx3, modifier = Modifier.size(18.dp))
        }
    }
}

@Suppress("unused") private val spacing = Arrangement.spacedBy(0.dp)
