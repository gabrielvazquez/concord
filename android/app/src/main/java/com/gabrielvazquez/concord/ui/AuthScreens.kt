package com.gabrielvazquez.concord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.Screen
import com.gabrielvazquez.concord.net.SavedAccount

val SWATCHES = listOf("#6366f1", "#8b5cf6", "#ec4899", "#ef4444", "#f97316", "#f59e0b", "#22c55e", "#14b8a6", "#06b6d4", "#3b82f6")

@Composable
fun Backdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.bg).background(Brush.radialGradient(listOf(Color(0xFF1B1B20), Color.Transparent), radius = 1400f, center = androidx.compose.ui.geometry.Offset(540f, -200f)))) { content() }
}

@Composable
fun field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, kb: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else kb),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.tx2, unfocusedBorderColor = C.line2, focusedContainerColor = C.card, unfocusedContainerColor = C.card, focusedLabelColor = C.tx2, cursorColor = Color.White),
    )
}

@Composable
fun PrimaryButton(text: String, busy: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black, disabledContainerColor = C.card2, disabledContentColor = C.tx3)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black) else Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Tela de contas salvas (entrar com um toque) */
@Composable
fun AccountsScreen() {
    val c = Concord.I
    var list by remember { mutableStateOf(c.accounts()) }
    var sel by remember { mutableStateOf(list.firstOrNull()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    Backdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(40.dp))
            Logo(64.dp)
            Spacer(Modifier.height(14.dp))
            Text("C O N C O R D", color = C.tx2, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Spacer(Modifier.height(36.dp))
            val a = sel
            if (a != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(24.dp)).padding(vertical = 28.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(112.dp).clip(CircleShape).border(1.dp, C.line2, CircleShape).padding(4.dp)) { AvatarRaw(a.avatar, a.name, a.color, 104.dp) }
                Spacer(Modifier.height(14.dp))
                Text(a.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.card).border(1.dp, C.line2, RoundedCornerShape(24.dp))
                    .clickable(enabled = !busy) { busy = true; err = null; c.enterAccount(a) { busy = false; err = it } }.padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Entrar", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                        else Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
                err?.let { Spacer(Modifier.height(10.dp)); Text(it, color = C.red, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(18.dp))
            list.filter { it.id != sel?.id }.forEach { o ->
                Row(Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(24.dp))
                    .clickable { if (editing) { c.forget(o); list = c.accounts(); if (sel?.id == o.id) sel = list.firstOrNull() } else sel = o }.padding(5.dp, 5.dp, 16.dp, 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AvatarRaw(o.avatar, o.name, o.color, 32.dp); Spacer(Modifier.width(10.dp)); Text(o.name, color = C.tx2, fontWeight = FontWeight.SemiBold)
                    if (editing) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Close, null, tint = C.red, modifier = Modifier.size(16.dp)) }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { c.screen = Screen.Login() }) { Icon(Icons.Default.Add, null, tint = C.tx3, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Outra conta", color = C.tx3) }
                TextButton(onClick = {
                    if (list.size <= 1 && sel != null) { c.forget(sel!!); list = c.accounts(); sel = list.firstOrNull() } else editing = !editing
                }) { Icon(Icons.Default.Close, null, tint = C.tx3, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (editing) "Pronto" else "Remover", color = C.tx3) }
            }
        }
    }
}

@Composable
fun LoginScreen(s: Screen.Login) {
    val c = Concord.I
    var mode by remember { mutableStateOf(if (s.signup) "signup" else "login") }
    var email by remember { mutableStateOf(s.email) }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(SWATCHES.random()) }
    var msg by remember { mutableStateOf(s.msg) }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Backdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
            Spacer(Modifier.height(30.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Logo(30.dp); Spacer(Modifier.width(10.dp)); Text("CONCORD", fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp, fontSize = 14.sp) }
            Spacer(Modifier.height(28.dp))
            Text(when (mode) { "signup" -> "Criar conta"; "forgot" -> "Esqueceu a senha?"; else -> "Bem-vindo de volta" }, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(20.dp))
            msg?.let {
                Text(it, color = if (ok) C.green else C.red, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (ok) Color(0x1A3DDC84) else Color(0x1AFF4D5E)).padding(12.dp))
                Spacer(Modifier.height(14.dp))
            }
            if (mode == "signup") { field(name, { name = it.take(24) }, "Como te chamam"); Spacer(Modifier.height(12.dp)) }
            field(email, { email = it }, "Email", kb = KeyboardType.Email)
            if (mode != "forgot") { Spacer(Modifier.height(12.dp)); field(pass, { pass = it }, "Senha", password = true) }
            if (mode == "signup") {
                Spacer(Modifier.height(14.dp)); Text("Cor do avatar", color = C.tx2, fontSize = 13.sp); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SWATCHES.forEach { h -> Box(Modifier.size(26.dp).clip(CircleShape).background(parseColor(h)).then(if (h == color) Modifier.border(2.dp, Color.White, CircleShape) else Modifier).clickable { color = h }) }
                }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(when (mode) { "signup" -> "Criar conta"; "forgot" -> "Enviar link"; else -> "Entrar" }, busy, email.isNotBlank() && (mode == "forgot" || pass.length >= 6) && (mode != "signup" || name.isNotBlank())) {
                busy = true; msg = null; ok = false
                when (mode) {
                    "login" -> c.signIn(email, pass) { busy = false; msg = it }
                    "signup" -> c.signUp(name, email, pass, color, { busy = false; msg = it }) { busy = false; ok = true; mode = "login"; msg = "Conta criada! Confirme pelo link que enviamos para $email e depois entre aqui." }
                    else -> c.recover(email) { busy = false; ok = true; msg = it }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (mode == "login") TextButton(onClick = { mode = "forgot"; msg = null }, modifier = Modifier.align(Alignment.End)) { Text("Esqueci minha senha", color = C.tx3) }
            Spacer(Modifier.height(8.dp))
            Text(if (mode == "login") "Primeira vez aqui? Criar conta" else "Já tenho conta · Entrar", color = C.tx2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { mode = if (mode == "login") "signup" else "login"; msg = null }.padding(12.dp))
            if (c.accounts().isNotEmpty()) Text("Voltar para as contas", color = C.tx3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { c.screen = Screen.Accounts }.padding(8.dp))
        }
    }
}

@Composable
fun NewProfileScreen() {
    val c = Concord.I
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(SWATCHES.random()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Backdrop {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Seu perfil", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(20.dp))
            msg?.let { Text(it, color = C.red); Spacer(Modifier.height(10.dp)) }
            field(name, { name = it.take(24) }, "Nome"); Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SWATCHES.forEach { h -> Box(Modifier.size(26.dp).clip(CircleShape).background(parseColor(h)).then(if (h == color) Modifier.border(2.dp, Color.White, CircleShape) else Modifier).clickable { color = h }) } }
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Continuar", busy, name.isNotBlank()) { busy = true; c.createProfile(name, color) { busy = false; msg = it } }
        }
    }
}

@Suppress("unused") private fun unused(a: SavedAccount) = a
