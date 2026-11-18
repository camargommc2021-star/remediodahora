package com.meuapp.lembretes.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.Remedio
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun TelaRemedios(abrir: (Remedio) -> Unit, acaoCabecalho: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val remedios by Dados.remedios.collectAsState()
    val tomados by Dados.tomados.collectAsState()
    val extras = LocalExtras.current

    // Atualiza "agora" a cada 30 s para marcar próxima/atrasada
    var agora by remember { mutableStateOf(horaAgora()) }
    var hoje by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            agora = horaAgora()
            hoje = LocalDate.now().toString()
        }
    }

    val ativos = remedios.filter { it.ativoEm(hoje) }
    val encerrados = remedios.filter { !it.ativoEm(hoje) }
    val porHora = ativos
        .flatMap { r -> r.horarios.map { h -> h to r } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
    val doses = porHora.flatMap { (h, rs) -> rs.map { Dados.chave(it.id, hoje, h) } }
    val feitas = doses.count { it in tomados }
    fun pendente(h: String, rs: List<Remedio>) = rs.any { Dados.chave(it.id, hoje, h) !in tomados }
    val proxima = porHora.entries.firstOrNull { (h, rs) -> h >= agora && pendente(h, rs) }?.key

    LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
        item {
            Cabecalho("Hoje", dataLonga(hoje), acao = acaoCabecalho)
        }

        // Tira de blister: uma cápsula por dose do dia
        if (doses.isNotEmpty()) {
            item { Blister(doses = doses, tomados = tomados, feitas = feitas) }
        }

        val acabando = ativos.filter { it.estoqueAcabando() }
        if (acabando.isNotEmpty()) {
            item { RotuloSecao("Comprar") }
            item {
                Grupo {
                    acabando.forEachIndexed { i, r ->
                        if (i > 0) Divisoria()
                        Row(
                            Modifier.fillMaxWidth().clickable { abrir(r) }.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(extras.ambar))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.nome, style = MaterialTheme.typography.titleMedium)
                                Text(textoEstoque(r), style = MaterialTheme.typography.bodySmall, color = extras.ambar)
                            }
                        }
                    }
                }
            }
        }

        item { RotuloSecao("Doses do dia") }
        item {
            Grupo {
                if (porHora.isEmpty()) {
                    TextoVazio("Nenhuma dose para hoje. Toque em \"Remédio\" para cadastrar, ou importe uma receita em Configurações.")
                }
                porHora.entries.forEachIndexed { i, (h, rs) ->
                    if (i > 0) Divisoria(recuo = 92)
                    val estaPendente = pendente(h, rs)
                    val status = when {
                        !estaPendente -> null
                        h == proxima -> "Próxima"
                        h < agora -> "Atrasada"
                        else -> null
                    }
                    val corHora = when {
                        !estaPendente -> extras.folha
                        status == "Atrasada" -> extras.ambar
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)) {
                        Column(Modifier.width(74.dp)) {
                            Text(h, style = EstiloHora, color = corHora)
                            if (status != null) {
                                Text(status, style = MaterialTheme.typography.labelMedium, color = extras.ambar)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            rs.forEach { r ->
                                val tomado = Dados.chave(r.id, hoje, h) in tomados
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(r.nome, style = MaterialTheme.typography.titleMedium)
                                        if (r.dose.isNotBlank()) {
                                            Text(r.dose, style = MaterialTheme.typography.bodySmall, color = extras.apagado)
                                        }
                                    }
                                    BotaoTomei(tomado) { Dados.marcarTomado(ctx, r.id, hoje, h, !tomado) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (ativos.isNotEmpty()) {
            item { RotuloSecao("Em uso") }
            item { ListaRemedios(ativos, abrir) }
        }
        if (encerrados.isNotEmpty()) {
            item { RotuloSecao("Tratamentos encerrados") }
            item { Box(Modifier.alpha(0.6f)) { ListaRemedios(encerrados, abrir) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Blister(doses: List<String>, tomados: Set<String>, feitas: Int) {
    val extras = LocalExtras.current
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            doses.forEach { chave ->
                val cor by animateColorAsState(
                    if (chave in tomados) extras.folha else extras.linha,
                    label = "capsula"
                )
                Box(Modifier.size(width = 24.dp, height = 11.dp).clip(CircleShape).background(cor))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (feitas == doses.size) "Todas as ${doses.size} doses tomadas"
            else "$feitas de ${doses.size} doses tomadas",
            style = MaterialTheme.typography.bodyMedium,
            color = if (feitas == doses.size) extras.folha else extras.apagado
        )
    }
}

@Composable
private fun ListaRemedios(lista: List<Remedio>, abrir: (Remedio) -> Unit) {
    val extras = LocalExtras.current
    Grupo {
        lista.forEachIndexed { i, r ->
            if (i > 0) Divisoria()
            Column(
                Modifier.fillMaxWidth().clickable { abrir(r) }.padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(r.nome, style = MaterialTheme.typography.titleMedium)
                val detalhe = buildString {
                    if (r.dose.isNotBlank()) append(r.dose).append('\n')
                    append(listaHorarios(r.horarios))
                    if (r.fim != null) append(", até ").append(diaMes(r.fim))
                }
                Text(detalhe, style = MaterialTheme.typography.bodySmall, color = extras.apagado)
                if (r.estoque != null) {
                    Text(
                        textoEstoque(r),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.estoqueAcabando()) extras.ambar else extras.folha
                    )
                }
            }
        }
    }
}

fun textoEstoque(r: Remedio): String {
    val e = r.estoque ?: return ""
    val dias = r.diasDeEstoque()
    val unidade = if (e == 1) "comprimido" else "comprimidos"
    return when {
        e == 0 -> "Acabou"
        dias == null -> "$e $unidade em casa"
        dias == 0 -> "$e $unidade em casa, não dá para amanhã"
        dias == 1 -> "$e $unidade em casa, dá para 1 dia"
        else -> "$e $unidade em casa, dá para $dias dias"
    }
}
