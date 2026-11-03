package uz.callguard.app.sync

import com.google.gson.JsonParser
import uz.callguard.app.data.SpamBackupCodec
import uz.callguard.app.data.SpamNumberEntity

object GlobalSpamFeed {
    fun parse(json: String, now: Long): List<SpamNumberEntity> {
        require(json.toByteArray(Charsets.UTF_8).size <= SpamBackupCodec.MAX_BYTES)
        val root = JsonParser.parseString(json.removePrefix("\uFEFF"))
        val records = if (root.isJsonArray) root.asJsonArray else {
            require(root.isJsonObject && root.asJsonObject.get("numbers")?.isJsonArray == true)
            root.asJsonObject.getAsJsonArray("numbers")
        }
        require(records.size() <= SpamBackupCodec.MAX_ENTRIES)
        records.forEach { record ->
            require(record.isJsonObject)
            val obj = record.asJsonObject
            if (!obj.has("phoneNumber") && obj.has("number")) obj.add("phoneNumber", obj.get("number"))
            val score = obj.get("spamScore") ?: obj.get("score")
            // Never silently promote an unscored network record to a blocking score of 100.
            require(score != null && score.isJsonPrimitive && score.asJsonPrimitive.isNumber) { "Missing numeric spamScore" }
            if (obj.has("score") && obj.has("spamScore")) require(obj.get("score") == score)
            obj.add("spamScore", score)
        }
        val normalized = if (root.isJsonObject && root.asJsonObject.has("format")) root.toString() else records.toString()
        return SpamBackupCodec.decode(normalized).map {
            it.copy(source = "global_sync", isGlobal = true, phoneNumber = null, updatedAt = now)
        }
    }
}
