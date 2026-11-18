package com.meuapp.lembretes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** Recebe os alarmes e o botão "Tomei" das notificações. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Dados.carregar(ctx)
        val id = intent.getLongExtra(Agendador.EXTRA_ID, -1L)
        when (intent.action) {
            Agendador.ACAO_REMEDIO -> {
                val hora = intent.getStringExtra(Agendador.EXTRA_HORA) ?: return
                val r = Dados.remedios.value.find { it.id == id } ?: return
                if (hora !in r.horarios) return
                val hoje = LocalDate.now().toString()
                if (r.ativoEm(hoje) && Dados.chave(id, hoje, hora) !in Dados.tomados.value) {
                    Notificacoes.mostrarRemedio(ctx, r, hoje, hora)
                }
                Agendador.agendarHorario(ctx, r, hora) // próximo dia
                WidgetDose.atualizar(ctx) // "próxima" vira "atrasada" no widget
            }
            Agendador.ACAO_COMPROMISSO -> {
                val c = Dados.compromissos.value.find { it.id == id } ?: return
                Notificacoes.mostrarCompromisso(ctx, c)
            }
            Agendador.ACAO_TOMEI -> {
                val hora = intent.getStringExtra(Agendador.EXTRA_HORA) ?: return
                val data = intent.getStringExtra(Agendador.EXTRA_DATA) ?: LocalDate.now().toString()
                Dados.marcarTomado(ctx, id, data, hora, true)
            }
        }
    }
}

/** Remarca todos os alarmes quando o celular liga, o app atualiza ou o relógio muda. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notificacoes.criarCanais(ctx)
        Agendador.agendarTudo(ctx)
        WidgetDose.atualizar(ctx)
    }
}
