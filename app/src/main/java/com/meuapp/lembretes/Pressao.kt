package com.meuapp.lembretes

/** Classificação da pressão (Diretriz Brasileira de Hipertensão, SBC 2020). */
object ClassificacaoPressao {
    val faixas = listOf(
        "Ótima",
        "Normal",
        "Pré-hipertensão",
        "Hipertensão estágio 1",
        "Hipertensão estágio 2",
        "Hipertensão estágio 3"
    )

    /** Nível 0..5; vale a maior faixa entre sistólica e diastólica. */
    fun nivel(s: Int, d: Int): Int {
        val ns = when {
            s >= 180 -> 5
            s >= 160 -> 4
            s >= 140 -> 3
            s >= 130 -> 2
            s >= 120 -> 1
            else -> 0
        }
        val nd = when {
            d >= 110 -> 5
            d >= 100 -> 4
            d >= 90 -> 3
            d >= 85 -> 2
            d >= 80 -> 1
            else -> 0
        }
        return maxOf(ns, nd)
    }
}
