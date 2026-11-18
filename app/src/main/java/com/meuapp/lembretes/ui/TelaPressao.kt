package com.meuapp.lembretes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.Medicao
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ================= Classificação (Diretriz Brasileira de Hipertensão, SBC 2020) =================

private val faixas = listOf(
    "Ótima",
    "Normal",
    "Pré-hipertensão",
    "Hipertensão estágio 1",
    "Hipertensão estágio 2",
    "Hipertensão estágio 3"
)

/** Retorna o nível 0..5; vale a maior faixa entre sistólica e diastólica. */
fun nivelPressao(s: Int, d: Int): Int {
    val ns = when {
        s >= 180 -> 5; s >= 160 -> 4; s >= 140 -> 3; s >= 130 -> 2; s >= 120 -> 1; else -> 0
    }
    val nd = when {
        d >= 110 -> 5; d >= 100 -> 4; d >= 90 -> 3; d >= 85 -> 2; d >= 80 -> 1; else -> 0
    }
    return maxOf(ns, nd)
}

@Composable
private fun corNivel(nivel: Int): Color {
    val extras = LocalExtras.current
    return when (nivel) {
        0, 1 -> extras.folha
        2 -> extras.ambar
        3 -> corAgenda(3) // coral
        else -> MaterialTheme.colorScheme.error
    }
}

// ================= Tela =================

@Composable
fun TelaPressao(abrir: (Medicao) -> Unit, acaoCabecalho: @Composable () -> Unit) {
    val medicoes by Dados.medicoes.collectAsState()
    val extras = LocalExtras.current

    val ultima = medicoes.firstOrNull()
    val semana = LocalDate.now().minusDays(6).toString()
    val ultimos7 = medicoes.filter { it.data >= semana }
    val grafico = medicoes.take(14).reversed()

    LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
        item { Cabecalho("Pressão", acao = acaoCabecalho) }

        if (ultima == null) {
            item {
                Grupo(Modifier.padding(top = 8.dp)) {
                    TextoVazio(
                        "Nenhuma medição ainda. Toque em \"Medição\" para anotar a primeira. " +
                            "Exemplo: 12 por 8 se anota como 120 e 80."
                    )
                }
            }
        } else {
            item {
                val nivel = nivelPressao(ultima.sistolica, ultima.diastolica)
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${ultima.sistolica}/${ultima.diastolica}",
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 52.sp, lineHeight = 56.sp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "mmHg",
                            style = MaterialTheme.typography.bodyLarge,
                            color = extras.apagado,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(corNivel(nivel)))
                        Spacer(Modifier.width(8.dp))
                        Text(faixas[nivel], style = MaterialTheme.typography.titleSmall, color = corNivel(nivel))
                    }
                    Text(
                        buildString {
                            append(quandoAmigavel(ultima))
                            if (ultima.pulso != null) append(", pulso ").append(ultima.pulso).append(" bpm")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = extras.apagado,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (ultimos7.size >= 2) {
                item {
                    val ms = ultimos7.map { it.sistolica }.average().roundToInt()
                    val md = ultimos7.map { it.diastolica }.average().roundToInt()
                    Text(
                        "Média dos últimos 7 dias: $ms/$md, em ${ultimos7.size} medições",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)
                    )
                }
            }

            if (grafico.size >= 2) {
                item { RotuloSecao("Evolução") }
                item {
                    Grupo {
                        GraficoPressao(grafico)
                    }
                }
            }

            item { RotuloSecao("Medições") }
            item {
                Grupo {
                    medicoes.take(60).forEachIndexed { i, m ->
                        if (i > 0) Divisoria(recuo = 92)
                        LinhaMedicao(m) { abrir(m) }
                    }
                }
            }
            item {
                Text(
                    "A classificação segue a Diretriz Brasileira de Hipertensão e serve de referência. " +
                        "Quem avalia o seu caso é o seu médico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.apagado,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp)
                )
            }
        }
    }
}

private fun quandoAmigavel(m: Medicao): String {
    val hoje = LocalDate.now().toString()
    val ontem = LocalDate.now().minusDays(1).toString()
    return when (m.data) {
        hoje -> "Hoje às ${m.hora}"
        ontem -> "Ontem às ${m.hora}"
        else -> "${diaMes(m.data)} às ${m.hora}"
    }
}

@Composable
private fun LinhaMedicao(m: Medicao, aoTocar: () -> Unit) {
    val extras = LocalExtras.current
    val nivel = nivelPressao(m.sistolica, m.diastolica)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoTocar)
            .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(74.dp)) {
            Text(m.hora, style = EstiloHora.copy(fontSize = 18.sp))
            Text(diaMes(m.data), style = MaterialTheme.typography.labelMedium, color = extras.apagado)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${m.sistolica}/${m.diastolica}", style = MaterialTheme.typography.headlineSmall)
                if (m.pulso != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "pulso ${m.pulso}",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.apagado,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            if (m.obs.isNotBlank()) {
                Text(m.obs, style = MaterialTheme.typography.bodySmall, color = extras.apagado)
            }
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(corNivel(nivel)))
    }
}

@Composable
private fun GraficoPressao(lista: List<Medicao>) {
    val extras = LocalExtras.current
    val corSis = MaterialTheme.colorScheme.primary
    val corDia = extras.folha
    val corLimite = extras.ambar
    val corGrade = extras.linha

    val minimo = 50f
    val maximo = maxOf(180, (lista.maxOf { it.sistolica } + 10)).toFloat()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            fun y(v: Int) = size.height * (1f - (v - minimo) / (maximo - minimo))
            fun x(i: Int) = if (lista.size == 1) size.width / 2 else i * size.width / (lista.size - 1)

            // grade discreta
            listOf(80, 120, 160).forEach { v ->
                drawLine(corGrade, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
            }
            // limites de referência 140 e 90
            val tracejado = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            listOf(140, 90).forEach { v ->
                drawLine(
                    corLimite.copy(alpha = 0.8f), Offset(0f, y(v)), Offset(size.width, y(v)),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = tracejado
                )
            }

            fun serie(valores: List<Int>, cor: Color) {
                val caminho = Path()
                valores.forEachIndexed { i, v ->
                    if (i == 0) caminho.moveTo(x(i), y(v)) else caminho.lineTo(x(i), y(v))
                }
                drawPath(caminho, cor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                valores.forEachIndexed { i, v ->
                    drawCircle(cor, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
                }
            }
            serie(lista.map { it.sistolica }, corSis)
            serie(lista.map { it.diastolica }, corDia)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legenda(corSis, "Sistólica")
            Legenda(corDia, "Diastólica")
            Legenda(corLimite, "Limites 140 e 90")
        }
    }
}

@Composable
private fun Legenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = LocalExtras.current.apagado)
    }
}

// ================= Formulário =================

@Composable
fun FormPressao(inicial: Medicao?, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val agora = LocalDateTime.now()
    var sis by remember { mutableStateOf(inicial?.sistolica?.toString() ?: "") }
    var dia by remember { mutableStateOf(inicial?.diastolica?.toString() ?: "") }
    var pulso by remember { mutableStateOf(inicial?.pulso?.toString() ?: "") }
    var obs by remember { mutableStateOf(inicial?.obs ?: "") }
    var data by remember { mutableStateOf(inicial?.data ?: agora.toLocalDate().toString()) }
    var hora by remember {
        mutableStateOf(inicial?.hora ?: agora.format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var confirmarExclusao by remember { mutableStateOf(false) }

    val s = sis.toIntOrNull()
    val d = dia.toIntOrNull()
    val p = pulso.toIntOrNull()
    val valido = s != null && d != null && s in 60..260 && d in 30..160 && s > d &&
        (pulso.isBlank() || (p != null && p in 25..250))

    FolhaCheia(
        titulo = if (inicial == null) "Nova medição" else "Editar medição",
        aoFechar = aoFechar,
        rotuloAcao = "Salvar",
        acaoAtiva = valido,
        aoAgir = {
            if (s != null && d != null) {
                Dados.salvarMedicao(
                    ctx,
                    Medicao(
                        id = inicial?.id ?: Dados.novoId(),
                        quando = "${data}T$hora",
                        sistolica = s,
                        diastolica = d,
                        pulso = p,
                        obs = obs.trim()
                    )
                )
            }
            aoFechar()
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto(sis, { sis = it.filter(Char::isDigit).take(3) }, "Sistólica", Modifier.weight(1f), teclado = KeyboardType.Number)
            CampoTexto(dia, { dia = it.filter(Char::isDigit).take(3) }, "Diastólica", Modifier.weight(1f), teclado = KeyboardType.Number)
        }
        CampoTexto(pulso, { pulso = it.filter(Char::isDigit).take(3) }, "Pulso (opcional)", teclado = KeyboardType.Number)

        if (s != null && d != null && s > d && s >= 60) {
            val nivel = nivelPressao(s, d)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(corNivel(nivel)))
                Spacer(Modifier.width(8.dp))
                Text(faixas[nivel], style = MaterialTheme.typography.titleSmall, color = corNivel(nivel))
            }
        } else {
            Text(
                "Anote em mmHg: 12 por 8 fica 120 e 80.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtras.current.apagado
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloCampo("Quando")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoValor(dataCurta(data), aoTocar = { escolherData(ctx, data) { data = it } })
                CampoValor(hora, aoTocar = { escolherHora(ctx, hora) { hora = it } })
            }
        }

        CampoTexto(obs, { obs = it }, "Observação, por exemplo braço esquerdo, após acordar", umaLinha = false)

        if (inicial != null) {
            TextButton(
                onClick = {
                    if (confirmarExclusao) {
                        Dados.excluirMedicao(ctx, inicial.id)
                        aoFechar()
                    } else confirmarExclusao = true
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(if (confirmarExclusao) "Toque de novo para excluir" else "Excluir medição") }
        }
    }
}
