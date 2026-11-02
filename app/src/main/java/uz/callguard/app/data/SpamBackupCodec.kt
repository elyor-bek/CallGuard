package uz.callguard.app.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import uz.callguard.app.heuristic.NumberFormatRules

/** Portable offline format; also accepts legacy arrays of records. */
object SpamBackupCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ENTRIES = 50_000
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    fun encode(numbers: List<SpamNumberEntity>): String = gson.toJson(JsonObject().apply {
        addProperty("format", "callguard-spam")
        addProperty("version", 2)
        add("numbers", gson.toJsonTree(numbers))
    })

    fun decode(json: String): List<SpamNumberEntity> {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "File too large" }
        val root = JsonParser.parseString(json.removePrefix("\uFEFF"))
        val array = if (root.isJsonArray) root.asJsonArray else {
            require(root.isJsonObject) { "Expected backup object" }
            val obj = root.asJsonObject
            require(string(obj, "format") == "callguard-spam" && integer(obj, "version", 0) in 1L..2L) { "Unsupported backup format" }
            require(obj.get("numbers")?.isJsonArray == true) { "Expected numbers array" }
            obj.getAsJsonArray("numbers")
        }
        require(array.size() <= MAX_ENTRIES) { "Too many entries" }
        return array.map { element ->
            require(element.isJsonObject) { "Expected number record" }
            val obj = element.asJsonObject
            val phone = string(obj, "phoneNumber")?.let { NumberFormatRules.check(it).normalized }
            if (phone != null) require(phone.matches(Regex("\\+?[0-9]{3,15}"))) { "Invalid phone number" }
            val suppliedHash = string(obj, "numberHash")?.lowercase()
            val hash = if (phone != null) HashUtils.sha256(phone) else requireNotNull(suppliedHash) { "Missing number" }
            require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid hash" }
            require(suppliedHash == null || suppliedHash == hash) { "Number and hash do not match" }
            val source = string(obj, "source") ?: "import"
            require(source.length in 1..64) { "Invalid source" }
            val reports = integer(obj, "reportCount", 1)
            require(reports in 1..Int.MAX_VALUE.toLong()) { "Invalid report count" }
            val addedAt = integer(obj, "addedAtEpochMs", System.currentTimeMillis())
            require(addedAt in 0..32_503_680_000_000L) { "Invalid timestamp" }
            val score = integer(obj, "spamScore", 100)
            require(score in 0..100) { "Invalid spam score" }
            val category = string(obj, "category")
            require(category == null || category.length <= 80) { "Invalid category" }
            val globalValue = obj.get("isGlobal")
            require(globalValue == null || (globalValue.isJsonPrimitive && globalValue.asJsonPrimitive.isBoolean))
            val updatedAt = integer(obj, "updatedAt", addedAt)
            require(updatedAt in 0..32_503_680_000_000L)
            SpamNumberEntity(hash, source, reports.toInt(), addedAt, phone, score.toInt(), category, globalValue?.asBoolean ?: false, updatedAt)
        }.distinctBy { it.numberHash }
    }

    private fun string(obj: JsonObject, key: String): String? {
        val value = obj.get(key) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid $key" }
        return value.asString
    }

    private fun integer(obj: JsonObject, key: String, default: Long): Long {
        val value: JsonElement = obj.get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Invalid $key" }
        return value.asBigDecimal.longValueExact()
    }
}
