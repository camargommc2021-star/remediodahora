package com.meuapp.lembretes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.meuapp.lembretes.ui.TelaPrincipal
import com.meuapp.lembretes.ui.Tema

class MainActivity : ComponentActivity() {

    private val pedirNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** PDF de receita recebido pelo "Compartilhar" ou "Abrir com". */
    private val pdfRecebido = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notificacoes.criarCanais(this)
        Dados.carregar(this)
        Agendador.agendarTudo(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) pdfRecebido.value = pdfDoIntent(intent)

        setContent {
            Tema {
                TelaPrincipal(
                    pdfRecebido = pdfRecebido.value,
                    aoUsarPdfRecebido = { pdfRecebido.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pdfDoIntent(intent)?.let { pdfRecebido.value = it }
    }

    private fun pdfDoIntent(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> i.data
        else -> null
    }
}
