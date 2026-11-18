package com.meuapp.lembretes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meuapp.lembretes.Compromisso
import com.meuapp.lembretes.Dados
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private fun descreverAviso(min: Int) = when (min) {
    0 -> "Aviso na hora"
    15 -> "Aviso 15 min antes"
    60 -> "Aviso 1 hora antes"
    1440 -> "Aviso 1 dia antes"
    else -> "Aviso $min min antes"
}

@Composable
fun TelaAgenda(
    diaSelecionado: String,
    aoSelecionar: (String) -> Unit,
    abrir: (Compromisso) -> Unit,
    novo: (String) -> Unit,
    acaoCabecalho: @Composable () -> Unit
) {
    val compromissos by Dados.compromissos.collectAsState()
    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.from(LocalDate.parse(diaSelecionado)).toString()) }
    val mes = YearMonth.parse(mesTexto)
    val hoje = LocalDate.now().toString()

    val diasComEvento = compromissos.map { it.data }.toSet()
    val doDia = compromissos.filter { it.data == diaSelecionado }
    val proximos = compromissos.filter { it.data >= hoje && it.data != diaSelecionado }.take(8)

    LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
        item { Cabecalho("Agenda", acao = acaoCabecalho) }
        item {
            Calendario(
                mes = mes,
                diaSelecionado = diaSelecionado,
                diasComEvento = diasComEvento,
                aoMudarMes = { mesTexto = it.toString() },
                aoSelecionar = aoSelecionar,
                aoIrParaHoje = {
                    mesTexto = YearMonth.now().toString()
                    aoSelecionar(hoje)
                }
            )
        }

        item { RotuloSecao(dataAmigavel(diaSelecionado)) }
        item {
            Grupo {
                if (doDia.isEmpty()) TextoVazio("Nada marcado neste dia.")
                doDia.forEachIndexed { i, c ->
                    if (i > 0) Divisoria(recuo = 92)
                    LinhaCompromisso(c, mostrarData = false) { abrir(c) }
                }
                if (diaSelecionado >= hoje) {
                    Divisoria(recuo = 0)
                    TextButton(
                        onClick = { novo(diaSelecionado) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Marcar em ${diaMes(diaSelecionado)}")
                    }
                }
            }
        }

        if (proximos.isNotEmpty()) {
            item { RotuloSecao("Próximos") }
            item {
                Grupo {
                    proximos.forEachIndexed { i, c ->
                        if (i > 0) Divisoria(recuo = 92)
                        LinhaCompromisso(c, mostrarData = true) { abrir(c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaCompromisso(c: Compromisso, mostrarData: Boolean, aoTocar: () -> Unit) {
    val extras = LocalExtras.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Column(Modifier.width(74.dp)) {
            Text(c.hora, style = EstiloHora)
            if (mostrarData) Text(diaMes(c.data), style = MaterialTheme.typography.labelMedium, color = extras.apagado)
        }
        Column(Modifier.weight(1f)) {
            Text(c.titulo, style = MaterialTheme.typography.titleMedium)
            if (c.obs.isNotBlank()) {
                Text(c.obs, style = MaterialTheme.typography.bodySmall, color = extras.apagado)
            }
            Text(descreverAviso(c.avisoMin), style = MaterialTheme.typography.bodySmall, color = extras.apagado)
        }
    }
}

@Composable
private fun Calendario(
    mes: YearMonth,
    diaSelecionado: String,
    diasComEvento: Set<String>,
    aoMudarMes: (YearMonth) -> Unit,
    aoSelecionar: (String) -> Unit,
    aoIrParaHoje: () -> Unit
) {
    val hoje = LocalDate.now()
    val cores = MaterialTheme.colorScheme
    val extras = LocalExtras.current

    Grupo(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mes.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (mes != YearMonth.from(hoje)) {
                TextButton(onClick = aoIrParaHoje) { Text("Hoje") }
            }
            IconButton(onClick = { aoMudarMes(mes.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
            }
            IconButton(onClick = { aoMudarMes(mes.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
            }
        }

        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row {
                listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.apagado
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
                            Modifier.weight(1f).aspectRatio(1f).padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (n in 1..diasNoMes) {
                                val data = mes.atDay(n)
                                val chave = data.toString()
                                val selecionado = chave == diaSelecionado
                                val ehHoje = data == hoje
                                val passado = data.isBefore(hoje)
                                var forma = Modifier.fillMaxSize().clip(CircleShape)
                                forma = if (selecionado) forma.background(cores.primary)
                                else if (ehHoje) forma.border(BorderStroke(1.5.dp, cores.primary), CircleShape)
                                else forma
                                Box(
                                    forma.clickable { aoSelecionar(chave) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "$n",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (ehHoje || selecionado) FontWeight.SemiBold else FontWeight.Normal,
                                            color = when {
                                                selecionado -> cores.onPrimary
                                                passado -> extras.apagado
                                                else -> cores.onSurface
                                            }
                                        )
                                        Box(
                                            Modifier.size(5.dp).clip(CircleShape).background(
                                                when {
                                                    chave !in diasComEvento -> Color.Transparent
                                                    selecionado -> cores.onPrimary
                                                    else -> extras.ambar
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
