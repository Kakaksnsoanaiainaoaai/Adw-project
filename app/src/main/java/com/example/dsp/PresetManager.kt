package com.example.dsp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.TestSignalMode
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.vdlmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "adw_vdlms_store_v2")

class PresetManager(private val context: Context) {

    private val keyUserPresets = stringPreferencesKey("user_presets_json_v2")
    private val keyLastPresetId = stringPreferencesKey("last_preset_id_v2")

    suspend fun loadAllPresets(): Pair<List<DspPreset>, String> {
        val prefs = context.vdlmsDataStore.data.first()
        val rawJson = prefs[keyUserPresets]
        val lastId = prefs[keyLastPresetId] ?: FactoryPresets.all.first().id
        val userPresets = if (!rawJson.isNullOrBlank()) parsePresets(rawJson) else emptyList()
        return Pair(FactoryPresets.all + userPresets, lastId)
    }

    suspend fun saveUserPreset(name: String, description: String, parameters: DspParameters): List<DspPreset> {
        val prefs = context.vdlmsDataStore.data.first()
        val existing = prefs[keyUserPresets]?.let(::parsePresets).orEmpty().toMutableList()
        val slotNum = String.format("U%02d", existing.size + 1)
        val newPreset = DspPreset(
            id = "user_${System.currentTimeMillis()}",
            slotNumber = slotNum,
            name = name.trim().ifEmpty { "USER $slotNum" }.uppercase().take(24),
            description = description.trim().ifEmpty { "Custom V2 user preset" },
            isFactory = false,
            parameters = parameters.copy(testSignalMode = TestSignalMode.LIVE_INPUT)
        )
        existing.add(newPreset)
        persistUserPresets(existing)
        setLastPresetId(newPreset.id)
        return FactoryPresets.all + existing
    }

    suspend fun renameUserPreset(id: String, newName: String, newDescription: String): List<DspPreset> {
        val prefs = context.vdlmsDataStore.data.first()
        val cleanName = newName.trim().uppercase().take(24)
        if (cleanName.isEmpty()) return loadAllPresets().first
        val updatedUser = prefs[keyUserPresets]?.let(::parsePresets).orEmpty().map { p ->
            if (p.id == id) {
                p.copy(
                    name = cleanName,
                    description = newDescription.trim().ifEmpty { p.description }
                )
            } else p
        }
        persistUserPresets(updatedUser)
        return FactoryPresets.all + updatedUser
    }

    suspend fun overwriteUserPreset(id: String, parameters: DspParameters): List<DspPreset> {
        val prefs = context.vdlmsDataStore.data.first()
        val updatedUser = prefs[keyUserPresets]?.let(::parsePresets).orEmpty().map { p ->
            if (p.id == id) {
                p.copy(parameters = parameters.copy(testSignalMode = TestSignalMode.LIVE_INPUT))
            } else p
        }
        persistUserPresets(updatedUser)
        return FactoryPresets.all + updatedUser
    }

    suspend fun deleteUserPreset(id: String): List<DspPreset> {
        val prefs = context.vdlmsDataStore.data.first()
        val existing = prefs[keyUserPresets]?.let(::parsePresets).orEmpty()
            .filterNot { it.id == id }
            .mapIndexed { index, p -> p.copy(slotNumber = String.format("U%02d", index + 1)) }
        persistUserPresets(existing)
        return FactoryPresets.all + existing
    }

    suspend fun resetAllUserPresets(): List<DspPreset> {
        persistUserPresets(emptyList())
        val firstFactory = FactoryPresets.all.first().id
        setLastPresetId(firstFactory)
        return FactoryPresets.all
    }

    suspend fun setLastPresetId(id: String) {
        context.vdlmsDataStore.edit { prefs ->
            prefs[keyLastPresetId] = id
        }
    }

    private suspend fun persistUserPresets(userPresets: List<DspPreset>) {
        val arr = JSONArray()
        for (p in userPresets) {
            arr.put(serializePreset(p))
        }
        context.vdlmsDataStore.edit { prefs ->
            prefs[keyUserPresets] = arr.toString()
        }
    }

    private fun serializePreset(p: DspPreset): JSONObject {
        val d = p.parameters
        val eqArr = JSONArray()
        for (b in d.eqBands) {
            eqArr.put(JSONObject().apply {
                put("index", b.index)
                put("name", b.name)
                put("enabled", b.enabled)
                put("shape", b.shape.code)
                put("freqHz", b.freqHz.toDouble())
                put("gainDb", b.gainDb.toDouble())
                put("q", b.q.toDouble())
            })
        }
        val paramsObj = JSONObject().apply {
            put("bypassDsp", d.bypassDsp)
            put("muteAll", d.muteAll)
            put("inputGainDb", d.inputGainDb.toDouble())
            put("inputMute", d.inputMute)
            put("inputInvertL", d.inputInvertL)
            put("inputInvertR", d.inputInvertR)
            put("eqBypass", d.eqBypass)
            put("eqBands", eqArr)
            put("xoverBypass", d.xoverBypass)
            put("hpfEnabled", d.hpfEnabled)
            put("hpfFreqHz", d.hpfFreqHz.toDouble())
            put("hpfFilterType", d.hpfFilterType.code)
            put("hpfSlopeDb", d.hpfSlopeDb.slopeDb)
            put("lpfEnabled", d.lpfEnabled)
            put("lpfFreqHz", d.lpfFreqHz.toDouble())
            put("lpfFilterType", d.lpfFilterType.code)
            put("lpfSlopeDb", d.lpfSlopeDb.slopeDb)
            put("delayEnabled", d.delayEnabled)
            put("delayLinked", d.delayLinked)
            put("delayMsL", d.delayMsL.toDouble())
            put("delayMsR", d.delayMsR.toDouble())
            put("limiterEnabled", d.limiterEnabled)
            put("limiterThresholdDb", d.limiterThresholdDb.toDouble())
            put("limiterAttackMs", d.limiterAttackMs.toDouble())
            put("limiterReleaseMs", d.limiterReleaseMs.toDouble())
            put("outputGainDb", d.outputGainDb.toDouble())
            put("outputMuteL", d.outputMuteL)
            put("outputMuteR", d.outputMuteR)
            put("outputInvertL", d.outputInvertL)
            put("outputInvertR", d.outputInvertR)
        }
        return JSONObject().apply {
            put("id", p.id)
            put("slotNumber", p.slotNumber)
            put("name", p.name)
            put("description", p.description)
            put("parameters", paramsObj)
        }
    }

    private fun parsePresets(json: String): List<DspPreset> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONObject("parameters")
                    val eqArr = p.getJSONArray("eqBands")
                    val parsedBands = buildList {
                        for (j in 0 until eqArr.length()) {
                            val b = eqArr.getJSONObject(j)
                            val shapeCode = b.optInt("shape", EqFilterShape.PEAK.code)
                            add(
                                EqBandState(
                                    index = b.getInt("index"),
                                    name = b.getString("name"),
                                    enabled = b.getBoolean("enabled"),
                                    shape = EqFilterShape.entries.firstOrNull { it.code == shapeCode } ?: EqFilterShape.PEAK,
                                    freqHz = b.getDouble("freqHz").toFloat(),
                                    gainDb = b.getDouble("gainDb").toFloat(),
                                    q = b.getDouble("q").toFloat()
                                )
                            )
                        }
                    }
                    val bands = if (parsedBands.size == 5) parsedBands else DspParameters.defaultEqBands()

                    val hpfTypeCode = p.optInt("hpfFilterType", XoverFilterType.LINKWITZ_RILEY.code)
                    val hpfSlopeCode = p.optInt("hpfSlopeDb", XoverSlopeDb.SLOPE_24.slopeDb)
                    val lpfTypeCode = p.optInt("lpfFilterType", XoverFilterType.LINKWITZ_RILEY.code)
                    val lpfSlopeCode = p.optInt("lpfSlopeDb", XoverSlopeDb.SLOPE_24.slopeDb)

                    add(
                        DspPreset(
                            id = o.getString("id"),
                            slotNumber = o.getString("slotNumber"),
                            name = o.getString("name"),
                            description = o.optString("description", "Custom V2 user preset"),
                            isFactory = false,
                            parameters = DspParameters(
                                bypassDsp = p.optBoolean("bypassDsp", false),
                                muteAll = p.optBoolean("muteAll", false),
                                inputGainDb = p.optDouble("inputGainDb", 0.0).toFloat(),
                                inputMute = p.optBoolean("inputMute", false),
                                inputInvertL = p.optBoolean("inputInvertL", false),
                                inputInvertR = p.optBoolean("inputInvertR", false),
                                eqBypass = p.optBoolean("eqBypass", false),
                                eqBands = bands,
                                xoverBypass = p.optBoolean("xoverBypass", false),
                                hpfEnabled = p.optBoolean("hpfEnabled", true),
                                hpfFreqHz = p.optDouble("hpfFreqHz", 40.0).toFloat(),
                                hpfFilterType = XoverFilterType.entries.firstOrNull { it.code == hpfTypeCode }
                                    ?: XoverFilterType.LINKWITZ_RILEY,
                                hpfSlopeDb = XoverSlopeDb.entries.firstOrNull { it.slopeDb == hpfSlopeCode }
                                    ?: XoverSlopeDb.SLOPE_24,
                                lpfEnabled = p.optBoolean("lpfEnabled", false),
                                lpfFreqHz = p.optDouble("lpfFreqHz", 18000.0).toFloat(),
                                lpfFilterType = XoverFilterType.entries.firstOrNull { it.code == lpfTypeCode }
                                    ?: XoverFilterType.LINKWITZ_RILEY,
                                lpfSlopeDb = XoverSlopeDb.entries.firstOrNull { it.slopeDb == lpfSlopeCode }
                                    ?: XoverSlopeDb.SLOPE_24,
                                delayEnabled = p.optBoolean("delayEnabled", false),
                                delayLinked = p.optBoolean("delayLinked", true),
                                delayMsL = p.optDouble("delayMsL", 0.0).toFloat(),
                                delayMsR = p.optDouble("delayMsR", 0.0).toFloat(),
                                limiterEnabled = p.optBoolean("limiterEnabled", true),
                                limiterThresholdDb = p.optDouble("limiterThresholdDb", -1.5).toFloat(),
                                limiterAttackMs = p.optDouble("limiterAttackMs", 2.0).toFloat(),
                                limiterReleaseMs = p.optDouble("limiterReleaseMs", 140.0).toFloat(),
                                outputGainDb = p.optDouble("outputGainDb", 0.0).toFloat(),
                                outputMuteL = p.optBoolean("outputMuteL", false),
                                outputMuteR = p.optBoolean("outputMuteR", false),
                                outputInvertL = p.optBoolean("outputInvertL", false),
                                outputInvertR = p.optBoolean("outputInvertR", false)
                            )
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
