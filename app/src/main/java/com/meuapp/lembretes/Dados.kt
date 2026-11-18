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
    val horarios: List<String>, // "HH:mm", em ordem
    val fim: String? = null, // último dia de uso "yyyy-MM-dd" (null = uso contínuo)
    val estoque: Int? = null, // comprimidos em casa (null = não controlar)
    val porDose: Int = 1, // comprimidos tomados em cada horário
    val avisouEstoque: Boolean = false // já avisou que está acabando
) {
    fun ativoEm(data: String) = fim == null || data <= fim

    /** Quantos dias o estoque ainda dura (null se não controla estoque). */
    fun diasDeEstoque(): Int? {
        val e = estoque ?: return null
        val porDia = porDose * horarios.size
        if (porDia <= 0) return null
        return e / porDia
    }

    fun estoqueAcabando(): Boolean = diasDeEstoque()?.let { it <= DIAS_AVISO_ESTOQUE } ?: false

    companion object {
        const val DIAS_AVISO_ESTOQUE = 7
    }
}

data class Compromisso(
    val id: Long,
    val titulo: String,
    val data: String, // "yyyy-MM-dd"
    val hora: String, // "HH:mm"
    val obs: String,
    val avisoMin: Int, // minutos antes para avisar
    val cor: Int = 0 // posição na lista de cores da agenda
)

data class Medicao(
    val id: Long,
    val quando: String, // "yyyy-MM-ddTHH:mm"
    val sistolica: Int,
    val diastolica: Int,
    val pulso: Int?,
    val obs: String
) {
    val data: String get() = quando.substring(0, 10)
    val hora: String get() = quando.substring(11, 16)
}

/** Guarda tudo no próprio celular (SharedPreferences em JSON). */
object Dados {
    private const val ARQUIVO = "dados"
    private const val VERSAO_BACKUP = 1

    private val _remedios = MutableStateFlow<List<Remedio>>(emptyList())
    val remedios: StateFlow<List<Remedio>> = _remedios

    private val _compromissos = MutableStateFlow<List<Compromisso>>(emptyList())
    val compromissos: StateFlow<List<Compromisso>> = _compromissos

    // Doses tomadas: "id|yyyy-MM-dd|HH:mm"
    private val _tomados = MutableStateFlow<Set<String>>(emptySet())
    val tomados: StateFlow<Set<String>> = _tomados

    private val _medicoes = MutableStateFlow<List<Medicao>>(emptyList())
    val medicoes: StateFlow<List<Medicao>> = _medicoes

    private val _mostrarPressao = MutableStateFlow(false)
    val mostrarPressao: StateFlow<Boolean> = _mostrarPressao

    private var carregado = false

    fun chave(id: Long, data: String, hora: String) = "$id|$data|$hora"

    // ---------------- JSON ----------------

    private fun lerRemedios(j: JSONArray) = (0 until j.length()).map { i ->
        val o = j.getJSONObject(i)
        val hs = o.getJSONArray("horarios")
        Remedio(
            id = o.getLong("id"),
            nome = o.getString("nome"),
            dose = o.optString("dose", ""),
            horarios = (0 until hs.length()).map { hs.getString(it) },
            fim = o.optString("fim", "").ifBlank { null },
            estoque = if (o.has("estoque")) o.getInt("estoque") else null,
            porDose = o.optInt("porDose", 1).coerceAtLeast(1),
            avisouEstoque = o.optBoolean("avisouEstoque", false)
        )
    }

    private fun lerCompromissos(j: JSONArray) = (0 until j.length()).map { i ->
        val o = j.getJSONObject(i)
        Compromisso(
            id = o.getLong("id"),
            titulo = o.getString("titulo"),
            data = o.getString("data"),
            hora = o.getString("hora"),
            obs = o.optString("obs", ""),
            avisoMin = o.optInt("avisoMin", 15),
            cor = o.optInt("cor", 0)
        )
    }

    private fun lerMedicoes(j: JSONArray) = (0 until j.length()).map { i ->
        val o = j.getJSONObject(i)
        Medicao(
            id = o.getLong("id"),
            quando = o.getString("quando"),
            sistolica = o.getInt("sis"),
            diastolica = o.getInt("dia"),
            pulso = o.optInt("pulso", -1).takeIf { it > 0 },
            obs = o.optString("obs", "")
        )
    }.sortedByDescending { it.quando }

    private fun jsonRemedios() = JSONArray().apply {
        _remedios.value.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("nome", r.nome)
                put("dose", r.dose)
                put("horarios", JSONArray(r.horarios))
                if (r.fim != null) put("fim", r.fim)
                if (r.estoque != null) put("estoque", r.estoque)
                put("porDose", r.porDose)
                put("avisouEstoque", r.avisouEstoque)
            })
        }
    }

    private fun jsonCompromissos() = JSONArray().apply {
        _compromissos.value.forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                put("titulo", c.titulo)
                put("data", c.data)
                put("hora", c.hora)
                put("obs", c.obs)
                put("avisoMin", c.avisoMin)
                put("cor", c.cor)
            })
        }
    }

    private fun jsonMedicoes() = JSONArray().apply {
        _medicoes.value.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id)
                put("quando", m.quando)
                put("sis", m.sistolica)
                put("dia", m.diastolica)
                if (m.pulso != null) put("pulso", m.pulso)
                put("obs", m.obs)
            })
        }
    }

    private fun filtrarTomados(t: Collection<String>): Set<String> {
        val limite = LocalDate.now().minusDays(30).toString() // histórico dos últimos 30 dias
        return t.filter { it.split("|").getOrNull(1).orEmpty() >= limite }.toSet()
    }

    // ---------------- Carregar e salvar ----------------

    @Synchronized
    fun carregar(ctx: Context) {
        if (carregado) return
        val p = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        _remedios.value = lerRemedios(JSONArray(p.getString("remedios", "[]") ?: "[]"))
        _compromissos.value = lerCompromissos(JSONArray(p.getString("compromissos", "[]") ?: "[]"))
        _medicoes.value = lerMedicoes(JSONArray(p.getString("pressao", "[]") ?: "[]"))
        _tomados.value = filtrarTomados(p.getStringSet("tomados", emptySet()) ?: emptySet())
        _mostrarPressao.value = p.getBoolean("mostrarPressao", false)
        carregado = true
    }

    private fun salvar(ctx: Context) {
        ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit()
            .putString("remedios", jsonRemedios().toString())
            .putString("compromissos", jsonCompromissos().toString())
            .putString("pressao", jsonMedicoes().toString())
            .putStringSet("tomados", HashSet(_tomados.value))
            .putBoolean("mostrarPressao", _mostrarPressao.value)
            .apply()
        WidgetDose.atualizar(ctx)
    }

    // ---------------- Backup em arquivo ----------------

    fun gerarBackup(ctx: Context): String {
        carregar(ctx)
        return JSONObject().apply {
            put("app", "remediodahora")
            put("versao", VERSAO_BACKUP)
            put("criadoEm", java.time.LocalDateTime.now().toString())
            put("remedios", jsonRemedios())
            put("compromissos", jsonCompromissos())
            put("pressao", jsonMedicoes())
            put("tomados", JSONArray(_tomados.value.toList()))
            put("mostrarPressao", _mostrarPressao.value)
        }.toString(2)
    }

    data class ResumoBackup(val remedios: Int, val compromissos: Int, val medicoes: Int, val criadoEm: String)

    /** Lê o arquivo sem aplicar. Lança exceção se não for um backup válido. */
    fun lerResumoBackup(texto: String): ResumoBackup {
        val o = JSONObject(texto)
        require(o.optString("app") == "remediodahora") { "Arquivo não é um backup do Remédio da Hora" }
        return ResumoBackup(
            remedios = o.optJSONArray("remedios")?.length() ?: 0,
            compromissos = o.optJSONArray("compromissos")?.length() ?: 0,
            medicoes = o.optJSONArray("pressao")?.length() ?: 0,
            criadoEm = o.optString("criadoEm", "")
        )
    }

    /** Substitui todos os dados pelos do backup e remarca os alarmes. */
    fun restaurarBackup(ctx: Context, texto: String) {
        carregar(ctx)
        val o = JSONObject(texto)
        require(o.optString("app") == "remediodahora") { "Arquivo não é um backup do Remédio da Hora" }
        val novosR = lerRemedios(o.optJSONArray("remedios") ?: JSONArray())
        val novosC = lerCompromissos(o.optJSONArray("compromissos") ?: JSONArray())
        val novasM = lerMedicoes(o.optJSONArray("pressao") ?: JSONArray())
        val jt = o.optJSONArray("tomados") ?: JSONArray()
        val novosT = filtrarTomados((0 until jt.length()).map { jt.getString(it) })

        // cancela os alarmes atuais antes de trocar
        _remedios.value.forEach { Agendador.cancelarRemedio(ctx, it) }
        _compromissos.value.forEach { Agendador.cancelarCompromisso(ctx, it.id) }

        _remedios.value = novosR.sortedBy { it.nome.lowercase() }
        _compromissos.value = novosC.sortedWith(compareBy({ it.data }, { it.hora }))
        _medicoes.value = novasM
        _tomados.value = novosT
        _mostrarPressao.value = o.optBoolean("mostrarPressao", _mostrarPressao.value)
        salvar(ctx)
        Agendador.agendarTudo(ctx)
    }

    private var ultimoId = 0L

    @Synchronized
    fun novoId(): Long {
        ultimoId = maxOf(System.currentTimeMillis(), ultimoId + 1)
        return ultimoId
    }

    // ---------- Remédios ----------

    fun salvarRemedio(ctx: Context, r: Remedio) {
        carregar(ctx)
        _remedios.value.find { it.id == r.id }?.let { Agendador.cancelarRemedio(ctx, it) }
        var ordenado = r.copy(horarios = r.horarios.distinct().sorted())
        // se o estoque foi reposto, libera um novo aviso no futuro
        if (!ordenado.estoqueAcabando()) ordenado = ordenado.copy(avisouEstoque = false)
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
        val jaEstava = k in _tomados.value
        if (tomado == jaEstava) return
        _tomados.value = if (tomado) _tomados.value + k else _tomados.value - k

        // estoque: desconta ao tomar, devolve ao desmarcar
        var avisar: Remedio? = null
        _remedios.value = _remedios.value.map { r ->
            if (r.id != id || r.estoque == null) return@map r
            val novo = (r.estoque + if (tomado) -r.porDose else r.porDose).coerceAtLeast(0)
            var atualizado = r.copy(estoque = novo)
            if (atualizado.estoqueAcabando() && !r.avisouEstoque && tomado) {
                atualizado = atualizado.copy(avisouEstoque = true)
                avisar = atualizado
            } else if (!atualizado.estoqueAcabando()) {
                atualizado = atualizado.copy(avisouEstoque = false)
            }
            atualizado
        }
        salvar(ctx)
        if (tomado) Notificacoes.cancelar(ctx, Agendador.codigoRemedio(id, hora))
        avisar?.let { Notificacoes.mostrarEstoque(ctx, it) }
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

    // ---------- Pressão ----------

    fun salvarMedicao(ctx: Context, m: Medicao) {
        carregar(ctx)
        _medicoes.value = (_medicoes.value.filter { it.id != m.id } + m).sortedByDescending { it.quando }
        salvar(ctx)
    }

    fun excluirMedicao(ctx: Context, id: Long) {
        carregar(ctx)
        _medicoes.value = _medicoes.value.filter { it.id != id }
        salvar(ctx)
    }

    fun definirMostrarPressao(ctx: Context, mostrar: Boolean) {
        carregar(ctx)
        _mostrarPressao.value = mostrar
        salvar(ctx)
    }
}
