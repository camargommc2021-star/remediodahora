package com.meuapp.lembretes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Remedio(
    val id: Long,
    val nome: String,
    val dose: String,
    val horarios: List<String> // "HH:mm", em ordem
)

data class Compromisso(
    val id: Long,
    val titulo: String,
    val data: String, // "yyyy-MM-dd"
    val hora: String, // "HH:mm"
    val obs: String,
    val avisoMin: Int // minutos antes para avisar
)

/** Guarda tudo no próprio celular (SharedPreferences em JSON). */
object Dados {
    private const val ARQUIVO = "dados"

    private val _remedios = MutableStateFlow<List<Remedio>>(emptyList())
    val remedios: StateFlow<List<Remedio>> = _remedios

    private val _compromissos = MutableStateFlow<List<Compromisso>>(emptyList())
    val compromissos: StateFlow<List<Compromisso>> = _compromissos

    // Doses tomadas: "id|yyyy-MM-dd|HH:mm"
    private val _tomados = MutableStateFlow<Set<String>>(emptySet())
    val tomados: StateFlow<Set<String>> = _tomados

    private var carregado = false

    fun chave(id: Long, data: String, hora: String) = "$id|$data|$hora"

    @Synchronized
    fun carregar(ctx: Context) {
        if (carregado) return
        val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

        val listaR = mutableListOf<Remedio>()
        val jr = JSONArray(p.getString("remedios", "[]") ?: "[]")
        for (i in 0 until jr.length()) {
            val o = jr.getJSONObject(i)
            val hs = o.getJSONArray("horarios")
            listaR += Remedio(
                id = o.getLong("id"),
                nome = o.getString("nome"),
                dose = o.optString("dose", ""),
                horarios = (0 until hs.length()).map { hs.getString(it) }
            )
        }

        val listaC = mutableListOf<Compromisso>()
        val jc = JSONArray(p.getString("compromissos", "[]") ?: "[]")
        for (i in 0 until jc.length()) {
            val o = jc.getJSONObject(i)
            listaC += Compromisso(
                id = o.getLong("id"),
                titulo = o.getString("titulo"),
                data = o.getString("data"),
                hora = o.getString("hora"),
                obs = o.optString("obs", ""),
                avisoMin = o.optInt("avisoMin", 15)
            )
        }

        // Mantém só o histórico de doses dos últimos 30 dias
        val limite = LocalDate.now().minusDays(30).toString()
        val t = (p.getStringSet("tomados", emptySet()) ?: emptySet())
            .filter { it.split("|").getOrNull(1).orEmpty() >= limite }
            .toSet()

        _remedios.value = listaR
        _compromissos.value = listaC
        _tomados.value = t
        carregado = true
    }

    private fun salvar(ctx: Context) {
        val jr = JSONArray()
        _remedios.value.forEach { r ->
            jr.put(JSONObject().apply {
                put("id", r.id)
                put("nome", r.nome)
                put("dose", r.dose)
                put("horarios", JSONArray(r.horarios))
            })
        }
        val jc = JSONArray()
        _compromissos.value.forEach { c ->
            jc.put(JSONObject().apply {
                put("id", c.id)
                put("titulo", c.titulo)
                put("data", c.data)
                put("hora", c.hora)
                put("obs", c.obs)
                put("avisoMin", c.avisoMin)
            })
        }
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("remedios", jr.toString())
            .putString("compromissos", jc.toString())
            .putStringSet("tomados", HashSet(_tomados.value))
            .apply()
    }

    fun novoId(): Long = System.currentTimeMillis()

    // ---------- Remédios ----------

    fun salvarRemedio(ctx: Context, r: Remedio) {
        carregar(ctx)
        _remedios.value.find { it.id == r.id }?.let { Agendador.cancelarRemedio(ctx, it) }
        val ordenado = r.copy(horarios = r.horarios.distinct().sorted())
        _remedios.value = (_remedios.value.filter { it.id != r.id } + ordenado).sortedBy { it.nome.lowercase() }
        salvar(ctx)
        Agendador.agendarRemedio(ctx, ordenado)
    }

    fun excluirRemedio(ctx: Context, id: Long) {
        carregar(ctx)
        _remedios.value.find { it.id == id }?.let { Agendador.cancelarRemedio(ctx, it) }
        _remedios.value = _remedios.value.filter { it.id != id }
        salvar(ctx)
    }

    fun marcarTomado(ctx: Context, id: Long, data: String, hora: String, tomado: Boolean) {
        carregar(ctx)
        val k = chave(id, data, hora)
        _tomados.value = if (tomado) _tomados.value + k else _tomados.value - k
        salvar(ctx)
        if (tomado) Notificacoes.cancelar(ctx, Agendador.codigoRemedio(id, hora))
    }

    // ---------- Agenda ----------

    fun salvarCompromisso(ctx: Context, c: Compromisso) {
        carregar(ctx)
        Agendador.cancelarCompromisso(ctx, c.id)
        _compromissos.value = (_compromissos.value.filter { it.id != c.id } + c)
            .sortedWith(compareBy({ it.data }, { it.hora }))
        salvar(ctx)
        Agendador.agendarCompromisso(ctx, c)
    }

    fun excluirCompromisso(ctx: Context, id: Long) {
        carregar(ctx)
        Agendador.cancelarCompromisso(ctx, id)
        _compromissos.value = _compromissos.value.filter { it.id != id }
        salvar(ctx)
    }
}
