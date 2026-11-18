package com.meuapp.lembretes.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ================= Datas e horas =================

val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val fmtCurta = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun dataCurta(data: String): String = LocalDate.parse(data).format(fmtCurta)
fun diaMes(data: String): String = LocalDate.parse(data).format(DateTimeFormatter.ofPattern("dd/MM"))

fun dataLonga(data: String): String =
    LocalDate.parse(data).format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR))
        .replaceFirstChar { it.uppercase() }

fun dataAmigavel(data: String): String {
    val d = LocalDate.parse(data)
    val hoje = LocalDate.now()
    return when (d) {
        hoje -> "Hoje, " + d.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ptBR))
        hoje.plusDays(1) -> "Amanhã, " + d.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ptBR))
        else -> dataLonga(data)
    }
}

fun horaAgora(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

fun listaHorarios(horarios: List<String>): String = when (horarios.size) {
    0 -> ""
    1 -> "Às ${horarios[0]}"
    else -> "Às " + horarios.dropLast(1).joinToString(", ") + " e " + horarios.last()
}

fun escolherHora(ctx: Context, atual: String, aoEscolher: (String) -> Unit) {
    val t = LocalTime.parse(atual)
    TimePickerDialog(ctx, { _, h, m ->
        aoEscolher(String.format(Locale.ROOT, "%02d:%02d", h, m))
    }, t.hour, t.minute, true).show()
}

fun escolherData(ctx: Context, atual: String, aoEscolher: (String) -> Unit) {
    val d = LocalDate.parse(atual)
    DatePickerDialog(ctx, { _, ano, mes, dia ->
        aoEscolher(LocalDate.of(ano, mes + 1, dia).toString())
    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

// ================= Peças de layout =================

/** Título grande de cada aba, com uma ação opcional à direita (ex.: configurações). */
@Composable
fun Cabecalho(titulo: String, subtitulo: String? = null, acao: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.displaySmall)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodyLarge, color = LocalExtras.current.apagado)
            }
        }
        acao?.invoke()
    }
}

@Composable
fun RotuloSecao(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = LocalExtras.current.apagado,
        modifier = modifier.padding(start = 28.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** Uma superfície que agrupa linhas relacionadas (em vez de um cartão para cada item). */
@Composable
fun Grupo(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = LocalExtras.current.papel
    ) {
        Column(content = conteudo)
    }
}

@Composable
fun Divisoria(recuo: Int = 16) {
    HorizontalDivider(
        Modifier.padding(start = recuo.dp),
        thickness = 1.dp,
        color = LocalExtras.current.linha
    )
}

@Composable
fun TextoVazio(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalExtras.current.apagado,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp)
    )
}

// ================= Controles =================

/** Opção em forma de cápsula. Selecionada fica preenchida com a cor da tinta. */
@Composable
fun Pilula(texto: String, selecionada: Boolean, aoTocar: () -> Unit, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    val fundo by animateColorAsState(if (selecionada) cores.primary else Color.Transparent, label = "fundo")
    val tinta by animateColorAsState(if (selecionada) cores.onPrimary else cores.onSurface, label = "tinta")
    Surface(
        onClick = aoTocar,
        shape = CircleShape,
        color = fundo,
        contentColor = tinta,
        border = if (selecionada) null else BorderStroke(1.dp, cores.outline),
        modifier = modifier
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

/** Botão de dose: contorno quando pendente, verde-folha quando tomada. */
@Composable
fun BotaoTomei(tomado: Boolean, aoTocar: () -> Unit) {
    val extras = LocalExtras.current
    val cores = MaterialTheme.colorScheme
    val fundo by animateColorAsState(if (tomado) extras.folha else Color.Transparent, label = "fundo")
    val tinta by animateColorAsState(if (tomado) extras.sobreFolha else cores.primary, label = "tinta")
    Surface(
        onClick = aoTocar,
        shape = CircleShape,
        color = fundo,
        contentColor = tinta,
        border = if (tomado) null else BorderStroke(1.5.dp, cores.primary)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tomado) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(if (tomado) "Tomado" else "Tomei", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Campo mostrado como valor tocável (data, hora). */
@Composable
fun CampoValor(valor: String, aoTocar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = aoTocar,
        shape = MaterialTheme.shapes.medium,
        color = LocalExtras.current.papel,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Text(
            valor,
            style = EstiloHora.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    umaLinha: Boolean = true
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = umaLinha,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = LocalExtras.current.papel,
            focusedContainerColor = LocalExtras.current.papel,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun TituloCampo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = LocalExtras.current.apagado)
}

// ================= Folha de tela cheia (formulários) =================

@Composable
fun FolhaCheia(
    titulo: String,
    aoFechar: () -> Unit,
    rotuloAcao: String? = null,
    acaoAtiva: Boolean = true,
    aoAgir: () -> Unit = {},
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar") }
                    Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (rotuloAcao != null) {
                        TextButton(onClick = aoAgir, enabled = acaoAtiva) {
                            Text(rotuloAcao, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    content = conteudo
                )
            }
        }
    }
}

val FormaGrupo = RoundedCornerShape(22.dp)
