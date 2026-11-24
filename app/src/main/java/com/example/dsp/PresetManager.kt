package com.example.dsp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.InputSourceRoute
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

    private fun serializeBands(bands: List<EqBandState>): JSONArray {
        val eqArr = JSONArray()
        for (b in bands) {
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
        return eqArr
    }

    private fun parseBands(eqArr: JSONArray?): List<EqBandState> {
        if (eqArr == null) return DspParameters.defaultEqBands()
        val parsed = buildList {
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
        return if (parsed.size == 5) parsed else DspParameters.defaultEqBands()
    }

    private fun serializePreset(p: DspPreset): JSONObject {
        val d = p.parameters
        val paramsObj = JSONObject().apply {
            put("bypassDsp", d.bypassDsp)
            put("muteAll", d.muteAll)

            put("inputGainDbL", d.inputGainDbL.toDouble())
            put("inputGainDbR", d.inputGainDbR.toDouble())
            put("inputLinked", d.inputLinked)
            put("inputMuteL", d.inputMuteL)
            put("inputMuteR", d.inputMuteR)
            put("inputInvertL", d.inputInvertL)
            put("inputInvertR", d.inputInvertR)
            put("routeSourceL", d.routeSourceL.code)
            put("routeSourceR", d.routeSourceR.code)

            put("eqChannelLinked", d.eqChannelLinked)
            put("eqBypassL", d.eqBypassL)
            put("eqBypassR", d.eqBypassR)
            put("eqBandsL", serializeBands(d.eqBandsL))
            put("eqBandsR", serializeBands(d.eqBandsR))

            put("xoverLinked", d.xoverLinked)
            put("xoverBypassL", d.xoverBypassL)
            put("hpfEnabledL", d.hpfEnabledL)
            put("hpfFreqHzL", d.hpfFreqHzL.toDouble())
            put("hpfFilterTypeL", d.hpfFilterTypeL.code)
            put("hpfSlopeDbL", d.hpfSlopeDbL.slopeDb)
            put("lpfEnabledL", d.lpfEnabledL)
            put("lpfFreqHzL", d.lpfFreqHzL.toDouble())
            put("lpfFilterTypeL", d.lpfFilterTypeL.code)
            put("lpfSlopeDbL", d.lpfSlopeDbL.slopeDb)

            put("xoverBypassR", d.xoverBypassR)
            put("hpfEnabledR", d.hpfEnabledR)
            put("hpfFreqHzR", d.hpfFreqHzR.toDouble())
            put("hpfFilterTypeR", d.hpfFilterTypeR.code)
            put("hpfSlopeDbR", d.hpfSlopeDbR.slopeDb)
            put("lpfEnabledR", d.lpfEnabledR)
            put("lpfFreqHzR", d.lpfFreqHzR.toDouble())
            put("lpfFilterTypeR", d.lpfFilterTypeR.code)
            put("lpfSlopeDbR", d.lpfSlopeDbR.slopeDb)

            put("delayEnabledL", d.delayEnabledL)
            put("delayEnabledR", d.delayEnabledR)
            put("delayLinked", d.delayLinked)
            put("delayMsL", d.delayMsL.toDouble())
            put("delayMsR", d.delayMsR.toDouble())

            put("limiterLinked", d.limiterLinked)
            put("limiterEnabledL", d.limiterEnabledL)
            put("limiterThresholdDbL", d.limiterThresholdDbL.toDouble())
            put("limiterAttackMsL", d.limiterAttackMsL.toDouble())
            put("limiterReleaseMsL", d.limiterReleaseMsL.toDouble())
            put("limiterEnabledR", d.limiterEnabledR)
            put("limiterThresholdDbR", d.limiterThresholdDbR.toDouble())
            put("limiterAttackMsR", d.limiterAttackMsR.toDouble())
            put("limiterReleaseMsR", d.limiterReleaseMsR.toDouble())

            put("outputGainDbL", d.outputGainDbL.toDouble())
            put("outputGainDbR", d.outputGainDbR.toDouble())
            put("outputLinked", d.outputLinked)
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

                    // Support both new per-channel keys and legacy single-channel keys
                    val legacyBands = parseBands(p.optJSONArray("eqBands"))
                    val bandsL = p.optJSONArray("eqBandsL")?.let(::parseBands) ?: legacyBands
                    val bandsR = p.optJSONArray("eqBandsR")?.let(::parseBands) ?: legacyBands

                    val legacyInGain = p.optDouble("inputGainDb", 0.0)
                    val legacyInMute = p.optBoolean("inputMute", false)
                    val legacyEqBypass = p.optBoolean("eqBypass", false)
                    val legacyXoverBypass = p.optBoolean("xoverBypass", false)
                    val legacyHpfEn = p.optBoolean("hpfEnabled", true)
                    val legacyHpfFreq = p.optDouble("hpfFreqHz", 40.0)
                    val legacyHpfType = p.optInt("hpfFilterType", XoverFilterType.LINKWITZ_RILEY.code)
                    val legacyHpfSlope = p.optInt("hpfSlopeDb", XoverSlopeDb.SLOPE_24.slopeDb)
                    val legacyLpfEn = p.optBoolean("lpfEnabled", false)
                    val legacyLpfFreq = p.optDouble("lpfFreqHz", 18000.0)
                    val legacyLpfType = p.optInt("lpfFilterType", XoverFilterType.LINKWITZ_RILEY.code)
                    val legacyLpfSlope = p.optInt("lpfSlopeDb", XoverSlopeDb.SLOPE_24.slopeDb)
                    val legacyDelayEn = p.optBoolean("delayEnabled", false)
                    val legacyLimEn = p.optBoolean("limiterEnabled", true)
                    val legacyLimThresh = p.optDouble("limiterThresholdDb", -1.5)
                    val legacyLimAtk = p.optDouble("limiterAttackMs", 2.0)
                    val legacyLimRel = p.optDouble("limiterReleaseMs", 140.0)
                    val legacyOutGain = p.optDouble("outputGainDb", 0.0)

                    fun filterTypeOf(code: Int) =
                        XoverFilterType.entries.firstOrNull { it.code == code } ?: XoverFilterType.LINKWITZ_RILEY

                    fun slopeOf(code: Int) =
                        XoverSlopeDb.entries.firstOrNull { it.slopeDb == code } ?: XoverSlopeDb.SLOPE_24

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
                                inputGainDbL = p.optDouble("inputGainDbL", legacyInGain).toFloat(),
                                inputGainDbR = p.optDouble("inputGainDbR", legacyInGain).toFloat(),
                                inputLinked = p.optBoolean("inputLinked", true),
                                inputMuteL = p.optBoolean("inputMuteL", legacyInMute),
                                inputMuteR = p.optBoolean("inputMuteR", legacyInMute),
                                inputInvertL = p.optBoolean("inputInvertL", false),
                                inputInvertR = p.optBoolean("inputInvertR", false),
                                routeSourceL = InputSourceRoute.fromCode(p.optInt("routeSourceL", 0)),
                                routeSourceR = InputSourceRoute.fromCode(p.optInt("routeSourceR", 1)),
                                eqChannelLinked = p.optBoolean("eqChannelLinked", true),
                                eqBypassL = p.optBoolean("eqBypassL", legacyEqBypass),
                                eqBypassR = p.optBoolean("eqBypassR", legacyEqBypass),
                                eqBandsL = bandsL,
                                eqBandsR = bandsR,
                                xoverLinked = p.optBoolean("xoverLinked", true),
                                xoverBypassL = p.optBoolean("xoverBypassL", legacyXoverBypass),
                                hpfEnabledL = p.optBoolean("hpfEnabledL", legacyHpfEn),
                                hpfFreqHzL = p.optDouble("hpfFreqHzL", legacyHpfFreq).toFloat(),
                                hpfFilterTypeL = filterTypeOf(p.optInt("hpfFilterTypeL", legacyHpfType)),
                                hpfSlopeDbL = slopeOf(p.optInt("hpfSlopeDbL", legacyHpfSlope)),
                                lpfEnabledL = p.optBoolean("lpfEnabledL", legacyLpfEn),
                                lpfFreqHzL = p.optDouble("lpfFreqHzL", legacyLpfFreq).toFloat(),
                                lpfFilterTypeL = filterTypeOf(p.optInt("lpfFilterTypeL", legacyLpfType)),
                                lpfSlopeDbL = slopeOf(p.optInt("lpfSlopeDbL", legacyLpfSlope)),
                                xoverBypassR = p.optBoolean("xoverBypassR", legacyXoverBypass),
                                hpfEnabledR = p.optBoolean("hpfEnabledR", legacyHpfEn),
                                hpfFreqHzR = p.optDouble("hpfFreqHzR", legacyHpfFreq).toFloat(),
                                hpfFilterTypeR = filterTypeOf(p.optInt("hpfFilterTypeR", legacyHpfType)),
                                hpfSlopeDbR = slopeOf(p.optInt("hpfSlopeDbR", legacyHpfSlope)),
                                lpfEnabledR = p.optBoolean("lpfEnabledR", legacyLpfEn),
                                lpfFreqHzR = p.optDouble("lpfFreqHzR", legacyLpfFreq).toFloat(),
                                lpfFilterTypeR = filterTypeOf(p.optInt("lpfFilterTypeR", legacyLpfType)),
                                lpfSlopeDbR = slopeOf(p.optInt("lpfSlopeDbR", legacyLpfSlope)),
                                delayEnabledL = p.optBoolean("delayEnabledL", legacyDelayEn),
                                delayEnabledR = p.optBoolean("delayEnabledR", legacyDelayEn),
                                delayLinked = p.optBoolean("delayLinked", true),
                                delayMsL = p.optDouble("delayMsL", 0.0).toFloat(),
                                delayMsR = p.optDouble("delayMsR", 0.0).toFloat(),
                                limiterLinked = p.optBoolean("limiterLinked", true),
                                limiterEnabledL = p.optBoolean("limiterEnabledL", legacyLimEn),
                                limiterThresholdDbL = p.optDouble("limiterThresholdDbL", legacyLimThresh).toFloat(),
                                limiterAttackMsL = p.optDouble("limiterAttackMsL", legacyLimAtk).toFloat(),
                                limiterReleaseMsL = p.optDouble("limiterReleaseMsL", legacyLimRel).toFloat(),
                                limiterEnabledR = p.optBoolean("limiterEnabledR", legacyLimEn),
                                limiterThresholdDbR = p.optDouble("limiterThresholdDbR", legacyLimThresh).toFloat(),
                                limiterAttackMsR = p.optDouble("limiterAttackMsR", legacyLimAtk).toFloat(),
                                limiterReleaseMsR = p.optDouble("limiterReleaseMsR", legacyLimRel).toFloat(),
                                outputGainDbL = p.optDouble("outputGainDbL", legacyOutGain).toFloat(),
                                outputGainDbR = p.optDouble("outputGainDbR", legacyOutGain).toFloat(),
                                outputLinked = p.optBoolean("outputLinked", true),
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
