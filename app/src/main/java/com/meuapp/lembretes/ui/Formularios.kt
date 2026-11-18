package com.meuapp.lembretes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meuapp.lembretes.Compromisso
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.Remedio
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

// ================= Remédio =================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormRemedio(
    inicial: Remedio?,
    ehNovo: Boolean,
    aoFechar: () -> Unit,
    aoSalvar: ((Remedio) -> Unit)? = null // na importação: devolve o remédio sem salvar
) {
    val ctx = LocalContext.current
    val extras = LocalExtras.current
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var dose by remember { mutableStateOf(inicial?.dose ?: "") }
    var fim by remember { mutableStateOf(inicial?.fim) }
    val horarios = remember { mutableStateListOf<String>().apply { addAll(inicial?.horarios ?: emptyList()) } }
    var confirmarExclusao by remember { mutableStateOf(false) }

    FolhaCheia(
        titulo = if (ehNovo) "Novo remédio" else "Editar remédio",
        aoFechar = aoFechar,
        rotuloAcao = "Salvar",
        acaoAtiva = nome.isNotBlank() && horarios.isNotEmpty(),
        aoAgir = {
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
    ) {
        CampoTexto(nome, { nome = it }, "Nome do remédio")
        CampoTexto(dose, { dose = it }, "Dose, por exemplo 1 comprimido de 50 mg")

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloCampo("Horários, todos os dias")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                horarios.sorted().forEach { h ->
                    Surface(
                        onClick = {
                            escolherHora(ctx, h) { novo ->
                                horarios.remove(h)
                                if (novo !in horarios) horarios.add(novo)
                            }
                        },
                        shape = CircleShape,
                        color = extras.folhaSuave
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(h, style = EstiloHora.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
                            Spacer(Modifier.width(4.dp))
                            Surface(onClick = { horarios.remove(h) }, shape = CircleShape, color = extras.folhaSuave) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remover $h",
                                    modifier = Modifier.padding(4.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
                Pilula("+ Horário", selecionada = false, aoTocar = {
                    escolherHora(ctx, horarios.maxOrNull()?.let { proximoHorario(it) } ?: "08:00") { h ->
                        if (h !in horarios) horarios.add(h)
                    }
                })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloCampo("Duração")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pilula("Uso contínuo", selecionada = fim == null, aoTocar = { fim = null })
                Pilula(
                    fim?.let { "Até ${dataCurta(it)}" } ?: "Até uma data",
                    selecionada = fim != null,
                    aoTocar = {
                        escolherData(ctx, fim ?: LocalDate.now().plusDays(6).toString()) { fim = it }
                    }
                )
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
}

private fun proximoHorario(h: String): String {
    val t = LocalTime.parse(h).plusHours(12)
    return String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)
}

// ================= Compromisso =================

private val opcoesAviso = listOf(
    0 to "Na hora",
    15 to "15 min antes",
    60 to "1 hora antes",
    1440 to "1 dia antes"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormCompromisso(inicial: Compromisso?, dataInicial: String, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val horaPadrao = String.format(Locale.ROOT, "%02d:00", (LocalTime.now().hour + 1) % 24)
    var titulo by remember { mutableStateOf(inicial?.titulo ?: "") }
    var data by remember { mutableStateOf(inicial?.data ?: dataInicial) }
    var hora by remember { mutableStateOf(inicial?.hora ?: horaPadrao) }
    var obs by remember { mutableStateOf(inicial?.obs ?: "") }
    var aviso by remember { mutableIntStateOf(inicial?.avisoMin ?: 60) }
    var cor by remember { mutableIntStateOf(inicial?.cor ?: 0) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    FolhaCheia(
        titulo = if (inicial == null) "Novo compromisso" else "Editar compromisso",
        aoFechar = aoFechar,
        rotuloAcao = "Salvar",
        acaoAtiva = titulo.isNotBlank(),
        aoAgir = {
            Dados.salvarCompromisso(
                ctx,
                Compromisso(inicial?.id ?: Dados.novoId(), titulo.trim(), data, hora, obs.trim(), aviso, cor)
            )
            aoFechar()
        }
    ) {
        CampoTexto(titulo, { titulo = it }, "O que é, por exemplo Consulta com cardiologista")

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloCampo("Quando")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoValor(dataCurta(data), aoTocar = { escolherData(ctx, data) { data = it } })
                CampoValor(hora, aoTocar = { escolherHora(ctx, hora) { hora = it } })
            }
            Text(
                dataAmigavel(data),
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtras.current.apagado
            )
        }

        CampoTexto(obs, { obs = it }, "Observação (opcional)", umaLinha = false)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloCampo("Cor: ${CoresAgenda.lista[CoresAgenda.indice(cor)].nome}")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CoresAgenda.lista.indices.forEach { i ->
                    EscolhaCor(corAgenda(i), selecionada = cor == i, nome = CoresAgenda.lista[i].nome) { cor = i }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloCampo("Avisar")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                opcoesAviso.forEach { (min, rotulo) ->
                    Pilula(rotulo, selecionada = aviso == min, aoTocar = { aviso = min })
                }
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
}

@Composable
private fun EscolhaCor(cor: Color, selecionada: Boolean, nome: String, aoTocar: () -> Unit) {
    val anel = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .then(if (selecionada) Modifier.border(2.dp, anel, CircleShape) else Modifier)
            .clickable(onClickLabel = nome, onClick = aoTocar)
            .padding(5.dp)
            .clip(CircleShape)
            .background(cor),
        contentAlignment = Alignment.Center
    ) {
        if (selecionada) {
            Icon(Icons.Filled.Check, contentDescription = "$nome escolhida", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
