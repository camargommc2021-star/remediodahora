package com.meuapp.lembretes.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.meuapp.lembretes.Compromisso
import com.meuapp.lembretes.Dados
import com.meuapp.lembretes.Medicao
import com.meuapp.lembretes.R
import com.meuapp.lembretes.Remedio
import java.time.LocalDate

private sealed class Dialogo {
    data class DeRemedio(val r: Remedio?) : Dialogo()
    data class DeCompromisso(val c: Compromisso?, val data: String) : Dialogo()
    data class DePressao(val m: Medicao?) : Dialogo()
}

@Composable
fun TelaPrincipal(pdfRecebido: Uri?, aoUsarPdfRecebido: () -> Unit) {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    var emConfig by rememberSaveable { mutableStateOf(false) }
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    var importando by remember { mutableStateOf<Uri?>(null) }
    var diaSelecionado by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val mostrarPressao by Dados.mostrarPressao.collectAsState()
    if (!mostrarPressao && aba == 2) aba = 0

    val escolherPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importando = uri
    }

    LaunchedEffect(pdfRecebido) {
        if (pdfRecebido != null) {
            importando = pdfRecebido
            aoUsarPdfRecebido()
        }
    }

    BackHandler(enabled = emConfig) { emConfig = false }

    val botaoConfig: @Composable () -> Unit = {
        IconButton(onClick = { emConfig = true }) {
            Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = LocalExtras.current.apagado)
        }
    }

    if (emConfig) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            TelaConfig(
                aoVoltar = { emConfig = false },
                aoImportar = { escolherPdf.launch(arrayOf("application/pdf")) }
            )
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    Divisoria(recuo = 0)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        val coresItem = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = LocalExtras.current.folhaSuave,
                            unselectedIconColor = LocalExtras.current.apagado,
                            unselectedTextColor = LocalExtras.current.apagado
                        )
                        NavigationBarItem(
                            selected = aba == 0,
                            onClick = { aba = 0 },
                            icon = { Icon(painterResource(R.drawable.ic_nav_remedios), contentDescription = null) },
                            label = { Text("Remédios") },
                            colors = coresItem
                        )
                        NavigationBarItem(
                            selected = aba == 1,
                            onClick = { aba = 1 },
                            icon = { Icon(painterResource(R.drawable.ic_nav_agenda), contentDescription = null) },
                            label = { Text("Agenda") },
                            colors = coresItem
                        )
                        if (mostrarPressao) {
                            NavigationBarItem(
                                selected = aba == 2,
                                onClick = { aba = 2 },
                                icon = { Icon(painterResource(R.drawable.ic_nav_pressao), contentDescription = null) },
                                label = { Text("Pressão") },
                                colors = coresItem
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        dialogo = when (aba) {
                            0 -> Dialogo.DeRemedio(null)
                            1 -> Dialogo.DeCompromisso(null, maxOf(diaSelecionado, LocalDate.now().toString()))
                            else -> Dialogo.DePressao(null)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(when (aba) { 0 -> "Remédio"; 1 -> "Compromisso"; else -> "Medição" }) }
                )
            }
        ) { espaco ->
            Box(Modifier.padding(espaco).fillMaxSize()) {
                if (aba == 0) {
                    TelaRemedios(
                        abrir = { dialogo = Dialogo.DeRemedio(it) },
                        acaoCabecalho = botaoConfig
                    )
                } else if (aba == 2) {
                    TelaPressao(
                        abrir = { dialogo = Dialogo.DePressao(it) },
                        acaoCabecalho = botaoConfig
                    )
                } else {
                    TelaAgenda(
                        diaSelecionado = diaSelecionado,
                        aoSelecionar = { diaSelecionado = it },
                        abrir = { dialogo = Dialogo.DeCompromisso(it, it.data) },
                        novo = { dialogo = Dialogo.DeCompromisso(null, it) },
                        acaoCabecalho = botaoConfig
                    )
                }
            }
        }
    }

    importando?.let { uri ->
        TelaImportar(uri, aoFechar = {
            importando = null
            emConfig = false
            aba = 0
        })
    }

    when (val d = dialogo) {
        is Dialogo.DeRemedio -> FormRemedio(d.r, ehNovo = d.r == null, aoFechar = { dialogo = null })
        is Dialogo.DeCompromisso -> FormCompromisso(d.c, d.data, aoFechar = { dialogo = null })
        is Dialogo.DePressao -> FormPressao(d.m, aoFechar = { dialogo = null })
        null -> {}
    }
}
