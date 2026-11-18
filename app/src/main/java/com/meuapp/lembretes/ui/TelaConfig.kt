package com.meuapp.lembretes.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.LeitorReceita
import com.meuapp.lembretes.Remedio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ================= Configurações =================

@Composable
fun TelaConfig(aoVoltar: () -> Unit, aoImportar: () -> Unit) {
    val ctx = LocalContext.current
    val extras = LocalExtras.current
    val versao = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = aoVoltar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
            Text(
                "Configurações",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
        }

        item { RotuloSecao("Receitas") }
        item {
            Grupo {
                LinhaConfig(
                    "Importar receita em PDF",
                    "Lê os remédios, as doses e os horários para você conferir antes de cadastrar.",
                    aoImportar
                )
            }
        }
        item {
            Text(
                "Também funciona pelo Compartilhar: no WhatsApp ou no e-mail, compartilhe o PDF e escolha Remédio da Hora.",
                style = MaterialTheme.typography.bodySmall,
                color = extras.apagado,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp)
            )
        }

        item { RotuloSecao("Abas") }
        item {
            val mostrarPressao by Dados.mostrarPressao.collectAsState()
            Grupo {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = mostrarPressao, onValueChange = { Dados.definirMostrarPressao(ctx, it) })
                        .padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Pressão arterial", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Mostra uma aba para anotar suas medições e ver a evolução.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.apagado
                        )
                    }
                    Switch(checked = mostrarPressao, onCheckedChange = null)
                }
            }
        }

        item { RotuloSecao("Lembretes") }
        item {
            Grupo {
                LinhaConfig("Notificações", "Som, vibração e como os avisos aparecem.") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
                Divisoria()
                LinhaConfig(
                    "Bateria",
                    "Em Bateria, escolha Sem restrições para os avisos não atrasarem."
                ) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            }
        }

        item {
            Text(
                "Remédio da Hora, versão $versao. Seus dados ficam guardados só neste celular.",
                style = MaterialTheme.typography.bodySmall,
                color = extras.apagado,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp)
            )
        }
    }
}

@Composable
private fun LinhaConfig(titulo: String, descricao: String, aoTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = LocalExtras.current.apagado)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LocalExtras.current.apagado
        )
    }
}

// ================= Importar receita =================

private data class ItemImportado(val r: Remedio, val incluir: Boolean)

@Composable
fun TelaImportar(uri: Uri, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val extras = LocalExtras.current
    var lendo by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var textoLido by remember { mutableStateOf("") }
    var verTexto by remember { mutableStateOf(false) }
    var ajustando by remember { mutableStateOf<Int?>(null) }
    val itens = remember { mutableStateListOf<ItemImportado>() }
    val selecionados = itens.count { it.incluir }

    LaunchedEffect(uri) {
        lendo = true
        erro = null
        try {
            val t = withContext(Dispatchers.IO) { LeitorReceita.extrairTexto(ctx, uri) }
            textoLido = t
            itens.clear()
            itens.addAll(LeitorReceita.interpretar(t).map { ItemImportado(it, true) })
        } catch (e: Exception) {
            erro = "Este arquivo não abriu como PDF. Confira se é a receita e tente de novo."
        }
        lendo = false
    }

    FolhaCheia(
        titulo = "Importar receita",
        aoFechar = aoFechar,
        rotuloAcao = if (lendo) null else "Cadastrar",
        acaoAtiva = selecionados > 0,
        aoAgir = {
            itens.filter { it.incluir }.forEach { Dados.salvarRemedio(ctx, it.r.copy(id = Dados.novoId())) }
            Toast.makeText(
                ctx,
                if (selecionados == 1) "1 remédio cadastrado" else "$selecionados remédios cadastrados",
                Toast.LENGTH_SHORT
            ).show()
            aoFechar()
        }
    ) {
        when {
            lendo -> Column(
                Modifier.fillMaxWidth().padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Lendo a receita…", style = MaterialTheme.typography.bodyLarge)
            }

            erro != null -> Text(erro ?: "", style = MaterialTheme.typography.bodyLarge)

            else -> {
                Text(
                    when (itens.size) {
                        0 -> "Nenhum remédio encontrado neste PDF. Cadastre pelo botão Remédio, na tela inicial."
                        1 -> "Encontrei 1 remédio. Confira com a receita e toque em Ajustar se precisar corrigir."
                        else -> "Encontrei ${itens.size} remédios. Confira com a receita e toque em Ajustar se precisar corrigir."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = extras.apagado
                )

                if (itens.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 0.dp)) {
                        // Grupo já tem margem lateral própria; aqui ocupa a largura do formulário
                        androidx.compose.material3.Surface(
                            shape = MaterialTheme.shapes.large,
                            color = extras.papel
                        ) {
                            Column {
                                itens.forEachIndexed { i, item ->
                                    if (i > 0) Divisoria(recuo = 60)
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.incluir,
                                            onCheckedChange = { itens[i] = item.copy(incluir = it) }
                                        )
                                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                            Text(item.r.nome, style = MaterialTheme.typography.titleMedium)
                                            val detalhe = buildString {
                                                if (item.r.dose.isNotBlank()) append(item.r.dose).append('\n')
                                                append(listaHorarios(item.r.horarios))
                                                if (item.r.fim != null) append(", até ").append(diaMes(item.r.fim))
                                            }
                                            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = extras.apagado)
                                        }
                                        TextButton(onClick = { ajustando = i }) { Text("Ajustar") }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Quando a receita diz \"a cada X horas\", a primeira dose fica às 08:00. Ajuste se preferir outro horário.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.apagado
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { verTexto = !verTexto }) {
                        Text(if (verTexto) "Esconder o texto lido" else "Ver o texto lido do PDF")
                    }
                    if (verTexto) {
                        Text(
                            textoLido.ifBlank { "Nenhum texto encontrado no arquivo." },
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.apagado
                        )
                    }
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
