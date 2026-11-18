package com.meuapp.lembretes.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meuapp.lembretes.Compromisso
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.LeitorReceita
import com.meuapp.lembretes.Remedio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ================= Tema =================

@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    val cores = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF7FD1BC), secondary = Color(0xFFF2A7A7))
    } else {
        lightColorScheme(primary = Color(0xFF2E7D6B), secondary = Color(0xFFC85A5A))
    }
    MaterialTheme(colorScheme = cores, content = conteudo)
}

// ================= Utilidades =================

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val fmtCurta = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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

private fun dataCurta(data: String) = LocalDate.parse(data).format(fmtCurta)

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

private fun descreverHorarios(r: Remedio): String {
    val base = "Todo dia às " + r.horarios.joinToString(", ")
    return if (r.fim == null) base else "$base • até ${dataCurta(r.fim)}"
}

private val opcoesAviso = listOf(
    0 to "Na hora",
    15 to "15 minutos antes",
    60 to "1 hora antes",
    1440 to "1 dia antes"
)

// ================= Tela principal =================

private sealed class Dialogo {
    data class DeRemedio(val r: Remedio?) : Dialogo()
    data class DeCompromisso(val c: Compromisso?, val data: String) : Dialogo()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPrincipal(pdfRecebido: Uri?, aoUsarPdfRecebido: () -> Unit) {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    var importando by remember { mutableStateOf<Uri?>(null) }
    var diaSelecionado by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    val escolherPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importando = uri
    }

    LaunchedEffect(pdfRecebido) {
        if (pdfRecebido != null) {
            aba = 0
            importando = pdfRecebido
            aoUsarPdfRecebido()
        }
    }

    val abas = listOf("Remédios" to "💊", "Agenda" to "📅")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (aba == 0) "Meus remédios" else "Minha agenda") })
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
            if (aba == 0) {
                ExtendedFloatingActionButton(onClick = { dialogo = Dialogo.DeRemedio(null) }) {
                    Text("+  Remédio")
                }
            } else {
                ExtendedFloatingActionButton(onClick = {
                    val hoje = LocalDate.now().toString()
                    dialogo = Dialogo.DeCompromisso(null, maxOf(diaSelecionado, hoje))
                }) { Text("+  Compromisso") }
            }
        }
    ) { espaco ->
        Box(Modifier.padding(espaco).fillMaxSize()) {
            if (aba == 0) {
                TelaRemedios(
                    abrir = { dialogo = Dialogo.DeRemedio(it) },
                    importar = { escolherPdf.launch(arrayOf("application/pdf")) }
                )
            } else {
                TelaAgenda(
                    diaSelecionado = diaSelecionado,
                    aoSelecionar = { diaSelecionado = it },
                    abrir = { dialogo = Dialogo.DeCompromisso(it, it.data) },
                    novo = { dialogo = Dialogo.DeCompromisso(null, it) }
                )
            }
        }
    }

    importando?.let { uri -> TelaImportar(uri, aoFechar = { importando = null }) }

    when (val d = dialogo) {
        is Dialogo.DeRemedio -> FormRemedio(d.r, ehNovo = d.r == null, aoFechar = { dialogo = null })
        is Dialogo.DeCompromisso -> FormCompromisso(d.c, d.data, aoFechar = { dialogo = null })
        null -> {}
    }
}

// ================= Peças comuns =================

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

@Composable
private fun CartaoRemedio(r: Remedio, aoTocar: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
        Column(Modifier.padding(14.dp)) {
            Text(r.nome, style = MaterialTheme.typography.titleMedium)
            if (r.dose.isNotBlank()) Text(r.dose, style = MaterialTheme.typography.bodyMedium)
            Text(
                descreverHorarios(r),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ================= Aba Remédios =================

@Composable
private fun TelaRemedios(abrir: (Remedio) -> Unit, importar: () -> Unit) {
    val ctx = LocalContext.current
    val remedios by Dados.remedios.collectAsState()
    val tomados by Dados.tomados.collectAsState()
    val hoje = LocalDate.now().toString()

    val ativos = remedios.filter { it.ativoEm(hoje) }
    val encerrados = remedios.filter { !it.ativoEm(hoje) }
    val doses = ativos.flatMap { r -> r.horarios.map { h -> r to h } }.sortedBy { it.second }
    val feitas = doses.count { (r, h) -> Dados.chave(r.id, hoje, h) in tomados }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(hojeExtenso(), style = MaterialTheme.typography.headlineSmall)
            if (doses.isNotEmpty()) {
                Text(
                    "$feitas de ${doses.size} doses tomadas hoje",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = importar, modifier = Modifier.fillMaxWidth()) {
                Text("📄  Importar receita em PDF")
            }
        }

        item { Secao("Doses de hoje") }
        if (doses.isEmpty()) item { Vazio("Nenhuma dose para hoje. Importe uma receita ou toque em \"+ Remédio\".") }
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

        if (ativos.isNotEmpty()) {
            item { Secao("Em uso") }
            items(ativos, key = { "a${it.id}" }) { r -> CartaoRemedio(r) { abrir(r) } }
        }
        if (encerrados.isNotEmpty()) {
            item { Secao("Tratamentos encerrados") }
            items(encerrados, key = { "e${it.id}" }) { r ->
                Box(Modifier.alpha(0.6f)) { CartaoRemedio(r) { abrir(r) } }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ================= Importar receita =================

private data class ItemImportado(val r: Remedio, val incluir: Boolean)

@Composable
private fun TelaImportar(uri: Uri, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var lendo by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var textoLido by remember { mutableStateOf("") }
    var verTexto by remember { mutableStateOf(false) }
    var ajustando by remember { mutableStateOf<Int?>(null) }
    val itens = remember { mutableStateListOf<ItemImportado>() }

    LaunchedEffect(uri) {
        lendo = true
        erro = null
        try {
            val t = withContext(Dispatchers.IO) { LeitorReceita.extrairTexto(ctx, uri) }
            textoLido = t
            itens.clear()
            itens.addAll(LeitorReceita.interpretar(t).map { ItemImportado(it, true) })
        } catch (e: Exception) {
            erro = "Não consegui abrir este PDF. Confira se o arquivo é mesmo a receita."
        }
        lendo = false
    }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Text("Importar receita", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        lendo -> Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Lendo a receita…")
                        }

                        erro != null -> Text(erro ?: "")

                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Text(
                                    if (itens.isEmpty())
                                        "Não encontrei remédios neste PDF. Você pode cadastrar pelo botão \"+ Remédio\"."
                                    else
                                        "Encontrei ${itens.size} remédio(s). Confira com a receita e toque em " +
                                            "\"Ajustar\" para corrigir nome, dose, horários ou duração.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            itemsIndexed(itens) { i, item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = item.incluir,
                                            onCheckedChange = { itens[i] = item.copy(incluir = it) }
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(item.r.nome, fontWeight = FontWeight.SemiBold)
                                            if (item.r.dose.isNotBlank()) {
                                                Text(item.r.dose, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                descreverHorarios(item.r),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        TextButton(onClick = { ajustando = i }) { Text("Ajustar") }
                                    }
                                }
                            }
                            item {
                                TextButton(onClick = { verTexto = !verTexto }) {
                                    Text(if (verTexto) "Esconder texto da receita" else "Ver texto lido da receita")
                                }
                            }
                            if (verTexto) {
                                item {
                                    Text(
                                        textoLido.ifBlank { "(nenhum texto encontrado)" },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                val selecionados = itens.count { it.incluir }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = aoFechar) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !lendo && selecionados > 0,
                        onClick = {
                            itens.filter { it.incluir }.forEach {
                                Dados.salvarRemedio(ctx, it.r.copy(id = Dados.novoId()))
                            }
                            Toast.makeText(ctx, "$selecionados remédio(s) cadastrado(s)", Toast.LENGTH_SHORT).show()
                            aoFechar()
                        }
                    ) { Text("Cadastrar ($selecionados)") }
                }
            }
        }
    }

    ajustando?.let { i ->
        itens.getOrNull(i)?.let { item ->
            FormRemedio(
                inicial = item.r,
                ehNovo = true,
                aoSalvar = { r -> itens[i] = ItemImportado(r, true) },
                aoFechar = { ajustando = null }
            )
        }
    }
}

// ================= Aba Agenda (com calendário) =================

@Composable
private fun TelaAgenda(
    diaSelecionado: String,
    aoSelecionar: (String) -> Unit,
    abrir: (Compromisso) -> Unit,
    novo: (String) -> Unit
) {
    val compromissos by Dados.compromissos.collectAsState()
    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.from(LocalDate.parse(diaSelecionado)).toString()) }
    val mes = YearMonth.parse(mesTexto)
    val hoje = LocalDate.now().toString()

    val diasComEvento = compromissos.map { it.data }.toSet()
    val doDia = compromissos.filter { it.data == diaSelecionado }
    val proximos = compromissos.filter { it.data >= hoje && it.data != diaSelecionado }.take(10)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Calendario(
                mes = mes,
                diaSelecionado = diaSelecionado,
                diasComEvento = diasComEvento,
                aoMudarMes = { mesTexto = it.toString() },
                aoSelecionar = aoSelecionar
            )
        }
        item { Secao(dataAmigavel(diaSelecionado)) }
        if (doDia.isEmpty()) item { Vazio("Nada marcado neste dia.") }
        items(doDia, key = { "d${it.id}" }) { c -> CartaoCompromisso(c, aoTocar = { abrir(c) }) }
        if (diaSelecionado >= hoje) {
            item {
                OutlinedButton(onClick = { novo(diaSelecionado) }) {
                    Text("+ Marcar em ${dataCurta(diaSelecionado).substring(0, 5)}")
                }
            }
        }
        if (proximos.isNotEmpty()) {
            item { Secao("Próximos compromissos") }
            items(proximos, key = { "p${it.id}" }) { c ->
                CartaoCompromisso(c, aoTocar = { abrir(c) }, mostrarData = true)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun Calendario(
    mes: YearMonth,
    diaSelecionado: String,
    diasComEvento: Set<String>,
    aoMudarMes: (YearMonth) -> Unit,
    aoSelecionar: (String) -> Unit
) {
    val hoje = LocalDate.now()
    val cores = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { aoMudarMes(mes.minusMonths(1)) }) { Text("‹", fontSize = 26.sp) }
                Text(
                    mes.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable {
                        aoMudarMes(YearMonth.from(hoje))
                        aoSelecionar(hoje.toString())
                    }
                )
                TextButton(onClick = { aoMudarMes(mes.plusMonths(1)) }) { Text("›", fontSize = 26.sp) }
            }
            Row {
                listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = cores.onSurfaceVariant
                    )
                }
            }
            val deslocamento = mes.atDay(1).dayOfWeek.value % 7 // domingo = 0
            val diasNoMes = mes.lengthOfMonth()
            val semanas = (deslocamento + diasNoMes + 6) / 7
            for (s in 0 until semanas) {
                Row {
                    for (d in 0 until 7) {
                        val n = s * 7 + d - deslocamento + 1
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (n in 1..diasNoMes) {
                                val data = mes.atDay(n)
                                val chave = data.toString()
                                val selecionado = chave == diaSelecionado
                                val ehHoje = data == hoje
                                val fundo = when {
                                    selecionado -> cores.primary
                                    ehHoje -> cores.primary.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                }
                                Box(
                                    Modifier.fillMaxSize().clip(CircleShape).background(fundo)
                                        .clickable { aoSelecionar(chave) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "$n",
                                            color = if (selecionado) cores.onPrimary else cores.onSurface,
                                            fontWeight = if (ehHoje) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (chave in diasComEvento) {
                                            Box(
                                                Modifier.size(5.dp).clip(CircleShape)
                                                    .background(if (selecionado) cores.onPrimary else cores.secondary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= Formulário de remédio =================

@Composable
private fun FormRemedio(
    inicial: Remedio?,
    ehNovo: Boolean,
    aoFechar: () -> Unit,
    aoSalvar: ((Remedio) -> Unit)? = null // usado na importação: devolve sem salvar
) {
    val ctx = LocalContext.current
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var dose by remember { mutableStateOf(inicial?.dose ?: "") }
    var fim by remember { mutableStateOf(inicial?.fim) }
    val horarios = remember { mutableStateListOf<String>().apply { addAll(inicial?.horarios ?: emptyList()) } }
    var confirmarExclusao by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (ehNovo) "Novo remédio" else "Editar remédio") },
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
                        Text(
                            h,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).clickable {
                                escolherHora(ctx, h) { novo ->
                                    horarios.remove(h)
                                    if (novo !in horarios) horarios.add(novo)
                                }
                            }
                        )
                        TextButton(onClick = { horarios.remove(h) }) { Text("Remover") }
                    }
                }
                OutlinedButton(onClick = {
                    escolherHora(ctx, "08:00") { h -> if (h !in horarios) horarios.add(h) }
                }) { Text("+ Adicionar horário") }

                Text("Duração", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val sugestao = fim ?: LocalDate.now().plusDays(6).toString()
                        escolherData(ctx, sugestao) { fim = it }
                    }) {
                        Text(fim?.let { "Até ${dataCurta(it)}" } ?: "Uso contínuo")
                    }
                    if (fim != null) {
                        TextButton(onClick = { fim = null }) { Text("Contínuo") }
                    }
                }

                if (!ehNovo && inicial != null) {
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
                    val r = Remedio(
                        id = if (inicial != null && inicial.id > 0) inicial.id else Dados.novoId(),
                        nome = nome.trim(),
                        dose = dose.trim(),
                        horarios = horarios.toList().sorted(),
                        fim = fim
                    )
                    if (aoSalvar != null) aoSalvar(r) else Dados.salvarRemedio(ctx, r)
                    aoFechar()
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } }
    )
}

// ================= Formulário de compromisso =================

@Composable
private fun FormCompromisso(inicial: Compromisso?, dataInicial: String, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val horaPadrao = String.format(Locale.ROOT, "%02d:00", (LocalTime.now().hour + 1) % 24)
    var titulo by remember { mutableStateOf(inicial?.titulo ?: "") }
    var data by remember { mutableStateOf(inicial?.data ?: dataInicial) }
    var hora by remember { mutableStateOf(inicial?.hora ?: horaPadrao) }
    var obs by remember { mutableStateOf(inicial?.obs ?: "") }
    var aviso by remember { mutableIntStateOf(inicial?.avisoMin ?: 60) }
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
                        Text("📅 " + dataCurta(data))
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
