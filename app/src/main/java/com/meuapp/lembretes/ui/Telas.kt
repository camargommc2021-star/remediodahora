package com.meuapp.lembretes.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.lembretes.Compromisso
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.Remedio
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------- Tema ----------------

@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    val cores = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF7FD1BC), secondary = Color(0xFFF2A7A7))
    } else {
        lightColorScheme(primary = Color(0xFF2E7D6B), secondary = Color(0xFFC85A5A))
    }
    MaterialTheme(colorScheme = cores, content = conteudo)
}

// ---------------- Utilidades ----------------

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

private fun dataAmigavel(data: String): String {
    val d = LocalDate.parse(data)
    val hoje = LocalDate.now()
    return when (d) {
        hoje -> "Hoje"
        hoje.plusDays(1) -> "Amanhã"
        hoje.minusDays(1) -> "Ontem"
        else -> d.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", ptBR))
            .replaceFirstChar { it.uppercase() }
    }
}

private fun hojeExtenso(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR))
        .replaceFirstChar { it.uppercase() }

private fun escolherHora(ctx: Context, atual: String, aoEscolher: (String) -> Unit) {
    val t = LocalTime.parse(atual)
    TimePickerDialog(ctx, { _, h, m ->
        aoEscolher(String.format(Locale.ROOT, "%02d:%02d", h, m))
    }, t.hour, t.minute, true).show()
}

private fun escolherData(ctx: Context, atual: String, aoEscolher: (String) -> Unit) {
    val d = LocalDate.parse(atual)
    DatePickerDialog(ctx, { _, ano, mes, dia ->
        aoEscolher(LocalDate.of(ano, mes + 1, dia).toString())
    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

private val opcoesAviso = listOf(
    0 to "Na hora",
    15 to "15 minutos antes",
    60 to "1 hora antes",
    1440 to "1 dia antes"
)

// ---------------- Tela principal ----------------

private sealed class Dialogo {
    data class DeRemedio(val r: Remedio?) : Dialogo()
    data class DeCompromisso(val c: Compromisso?) : Dialogo()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPrincipal() {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    val abas = listOf("Hoje" to "☀️", "Remédios" to "💊", "Agenda" to "📅")

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(if (aba == 0) "Remédio da Hora" else abas[aba].first)
            })
        },
        bottomBar = {
            NavigationBar {
                abas.forEachIndexed { i, (rotulo, emoji) ->
                    NavigationBarItem(
                        selected = aba == i,
                        onClick = { aba = i },
                        icon = { Text(emoji, fontSize = 22.sp) },
                        label = { Text(rotulo) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (aba) {
                1 -> ExtendedFloatingActionButton(onClick = { dialogo = Dialogo.DeRemedio(null) }) {
                    Text("+  Remédio")
                }
                2 -> ExtendedFloatingActionButton(onClick = { dialogo = Dialogo.DeCompromisso(null) }) {
                    Text("+  Compromisso")
                }
            }
        }
    ) { espaco ->
        Box(Modifier.padding(espaco).fillMaxSize()) {
            when (aba) {
                0 -> TelaHoje(
                    irParaRemedios = { aba = 1 },
                    abrirCompromisso = { dialogo = Dialogo.DeCompromisso(it) }
                )
                1 -> TelaRemedios(abrir = { dialogo = Dialogo.DeRemedio(it) })
                else -> TelaAgenda(abrir = { dialogo = Dialogo.DeCompromisso(it) })
            }
        }
    }

    when (val d = dialogo) {
        is Dialogo.DeRemedio -> FormRemedio(d.r, aoFechar = { dialogo = null })
        is Dialogo.DeCompromisso -> FormCompromisso(d.c, aoFechar = { dialogo = null })
        null -> {}
    }
}

// ---------------- Peças comuns ----------------

@Composable
private fun Secao(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun Vazio(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun CartaoCompromisso(c: Compromisso, aoTocar: () -> Unit, mostrarData: Boolean = false) {
    Card(Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.hora, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(76.dp))
            Column(Modifier.weight(1f)) {
                Text(c.titulo, fontWeight = FontWeight.SemiBold)
                if (mostrarData) Text(dataAmigavel(c.data), style = MaterialTheme.typography.bodySmall)
                if (c.obs.isNotBlank()) Text(c.obs, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------- Aba Hoje ----------------

@Composable
private fun TelaHoje(irParaRemedios: () -> Unit, abrirCompromisso: (Compromisso) -> Unit) {
    val ctx = LocalContext.current
    val remedios by Dados.remedios.collectAsState()
    val tomados by Dados.tomados.collectAsState()
    val compromissos by Dados.compromissos.collectAsState()
    val hoje = LocalDate.now().toString()

    val doses = remedios.flatMap { r -> r.horarios.map { h -> r to h } }.sortedBy { it.second }
    val agendaHoje = compromissos.filter { it.data == hoje }
    val proximos = compromissos.filter { it.data > hoje }.take(3)
    val feitas = doses.count { (r, h) -> Dados.chave(r.id, hoje, h) in tomados }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(hojeExtenso(), style = MaterialTheme.typography.headlineSmall)
            if (doses.isNotEmpty()) {
                Text(
                    "$feitas de ${doses.size} doses tomadas",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Secao("Remédios de hoje") }
        if (doses.isEmpty()) {
            item {
                Column {
                    Vazio("Você ainda não cadastrou remédios.")
                    OutlinedButton(onClick = irParaRemedios) { Text("Cadastrar remédio") }
                }
            }
        }
        items(doses, key = { "${it.first.id}-${it.second}" }) { (r, h) ->
            val tomado = Dados.chave(r.id, hoje, h) in tomados
            Card(
                Modifier.fillMaxWidth(),
                colors = if (tomado) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) else CardDefaults.cardColors()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(76.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.nome, fontWeight = FontWeight.SemiBold)
                        if (r.dose.isNotBlank()) Text(r.dose, style = MaterialTheme.typography.bodySmall)
                    }
                    if (tomado) {
                        OutlinedButton(onClick = { Dados.marcarTomado(ctx, r.id, hoje, h, false) }) {
                            Text("✓ Tomado")
                        }
                    } else {
                        Button(onClick = { Dados.marcarTomado(ctx, r.id, hoje, h, true) }) {
                            Text("Tomei")
                        }
                    }
                }
            }
        }

        item { Secao("Compromissos de hoje") }
        if (agendaHoje.isEmpty()) item { Vazio("Nada marcado para hoje.") }
        items(agendaHoje, key = { "h${it.id}" }) { c ->
            CartaoCompromisso(c, aoTocar = { abrirCompromisso(c) })
        }

        if (proximos.isNotEmpty()) {
            item { Secao("Próximos") }
            items(proximos, key = { "p${it.id}" }) { c ->
                CartaoCompromisso(c, aoTocar = { abrirCompromisso(c) }, mostrarData = true)
            }
        }
        item { Spacer(Modifier.padding(40.dp)) }
    }
}

// ---------------- Aba Remédios ----------------

@Composable
private fun TelaRemedios(abrir: (Remedio) -> Unit) {
    val remedios by Dados.remedios.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (remedios.isEmpty()) {
            item { Vazio("Toque em \"+ Remédio\" para cadastrar o primeiro.") }
        }
        items(remedios, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { abrir(r) }) {
                Column(Modifier.padding(14.dp)) {
                    Text(r.nome, style = MaterialTheme.typography.titleMedium)
                    if (r.dose.isNotBlank()) Text(r.dose, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Todo dia às " + r.horarios.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item { Spacer(Modifier.padding(40.dp)) }
    }
}

// ---------------- Aba Agenda ----------------

@Composable
private fun TelaAgenda(abrir: (Compromisso) -> Unit) {
    val compromissos by Dados.compromissos.collectAsState()
    val hoje = LocalDate.now().toString()
    val futuros = compromissos.filter { it.data >= hoje }.groupBy { it.data }
    val passados = compromissos.filter { it.data < hoje }.sortedByDescending { it.data }.take(20)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (futuros.isEmpty()) {
            item { Vazio("Nenhum compromisso pela frente. Toque em \"+ Compromisso\" para marcar.") }
        }
        futuros.forEach { (data, lista) ->
            item(key = "d$data") { Secao(dataAmigavel(data)) }
            items(lista, key = { it.id }) { c -> CartaoCompromisso(c, aoTocar = { abrir(c) }) }
        }
        if (passados.isNotEmpty()) {
            item { Secao("Anteriores") }
            items(passados, key = { "a${it.id}" }) { c ->
                Box(Modifier.alpha(0.6f)) {
                    CartaoCompromisso(c, aoTocar = { abrir(c) }, mostrarData = true)
                }
            }
        }
        item { Spacer(Modifier.padding(40.dp)) }
    }
}

// ---------------- Formulário de remédio ----------------

@Composable
private fun FormRemedio(inicial: Remedio?, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var dose by remember { mutableStateOf(inicial?.dose ?: "") }
    val horarios = remember { mutableStateListOf<String>().apply { addAll(inicial?.horarios ?: emptyList()) } }
    var confirmarExclusao by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (inicial == null) "Novo remédio" else "Editar remédio") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Nome do remédio") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dose, onValueChange = { dose = it },
                    label = { Text("Dose (ex.: 1 comprimido)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Horários (todo dia)", style = MaterialTheme.typography.titleSmall)
                if (horarios.isEmpty()) Vazio("Adicione pelo menos um horário.")
                horarios.sorted().forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { horarios.remove(h) }) { Text("Remover") }
                    }
                }
                OutlinedButton(onClick = {
                    escolherHora(ctx, "08:00") { h -> if (h !in horarios) horarios.add(h) }
                }) { Text("+ Adicionar horário") }

                if (inicial != null) {
                    TextButton(
                        onClick = {
                            if (confirmarExclusao) {
                                Dados.excluirRemedio(ctx, inicial.id)
                                aoFechar()
                            } else confirmarExclusao = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(if (confirmarExclusao) "Toque de novo para excluir" else "Excluir remédio") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank() && horarios.isNotEmpty(),
                onClick = {
                    Dados.salvarRemedio(
                        ctx,
                        Remedio(inicial?.id ?: Dados.novoId(), nome.trim(), dose.trim(), horarios.toList())
                    )
                    aoFechar()
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } }
    )
}

// ---------------- Formulário de compromisso ----------------

@Composable
private fun FormCompromisso(inicial: Compromisso?, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val horaPadrao = String.format(Locale.ROOT, "%02d:00", (LocalTime.now().hour + 1) % 24)
    var titulo by remember { mutableStateOf(inicial?.titulo ?: "") }
    var data by remember { mutableStateOf(inicial?.data ?: LocalDate.now().toString()) }
    var hora by remember { mutableStateOf(inicial?.hora ?: horaPadrao) }
    var obs by remember { mutableStateOf(inicial?.obs ?: "") }
    var aviso by remember { mutableIntStateOf(inicial?.avisoMin ?: 15) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (inicial == null) "Novo compromisso" else "Editar compromisso") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it },
                    label = { Text("O quê? (ex.: Consulta médica)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { escolherData(ctx, data) { data = it } }) {
                        Text("📅 " + LocalDate.parse(data).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    }
                    OutlinedButton(onClick = { escolherHora(ctx, hora) { hora = it } }) {
                        Text("🕐 $hora")
                    }
                }
                OutlinedTextField(
                    value = obs, onValueChange = { obs = it },
                    label = { Text("Observação (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Me avisar", style = MaterialTheme.typography.titleSmall)
                opcoesAviso.forEach { (min, rotulo) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = aviso == min, onClick = { aviso = min }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = aviso == min, onClick = null)
                        Text(rotulo, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                if (inicial != null) {
                    TextButton(
                        onClick = {
                            if (confirmarExclusao) {
                                Dados.excluirCompromisso(ctx, inicial.id)
                                aoFechar()
                            } else confirmarExclusao = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(if (confirmarExclusao) "Toque de novo para excluir" else "Excluir compromisso") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = titulo.isNotBlank(),
                onClick = {
                    Dados.salvarCompromisso(
                        ctx,
                        Compromisso(inicial?.id ?: Dados.novoId(), titulo.trim(), data, hora, obs.trim(), aviso)
                    )
                    aoFechar()
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } }
    )
}
