package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.HashUtils
import uz.callguard.app.data.SpamBackupCodec
import uz.callguard.app.data.SpamNumberEntity

class SpamBackupCodecTest {
    private val phone = "+998901234567"
    private val number = SpamNumberEntity(HashUtils.sha256(phone), "manual", 3, 1_700_000_000_000, phone)

    @Test fun roundTripsNumbersAndHashOnlyLegacyRecords() {
        val legacy = SpamNumberEntity("a".repeat(64), "assets", 1, 100, null)
        val records = listOf(number, legacy)
        assertEquals(records, SpamBackupCodec.decode(SpamBackupCodec.encode(records)))
    }

    @Test fun supportsPhoneOnlyRecordsAndDeduplicatesNormalizedNumbers() {
        val result = SpamBackupCodec.decode("""[{"phoneNumber":"+998 (90) 123-45-67"},{"phoneNumber":"+998901234567"}]""")
        assertEquals(1, result.size)
        assertEquals(phone, result.first().phoneNumber)
        assertEquals(number.numberHash, result.first().numberHash)
        assertEquals("import", result.first().source)
    }

    @Test fun supportsEmptyBackupAndUtf8ByteOrderMark() {
        assertEquals(emptyList<SpamNumberEntity>(), SpamBackupCodec.decode("\uFEFF" + SpamBackupCodec.encode(emptyList())))
    }

    @Test fun rejectsEntireFileWithAnyInvalidRecord() {
        expectFailure("""[{"phoneNumber":"+998901234567"},{"phoneNumber":"not-a-phone"}]""")
        expectFailure("""[{"numberHash":"uuid-or-invalid-hash"}]""")
        expectFailure("""[{"phoneNumber":"+998901234567","reportCount":-1}]""")
        expectFailure("""[{"phoneNumber":"+998901234567","addedAtEpochMs":1.5}]""")
    }

    @Test fun rejectsMismatchedPhoneAndHash() {
        expectFailure("""[{"phoneNumber":"+998901234567","numberHash":"${"a".repeat(64)}"}]""")
    }

    @Test fun rejectsUnknownFormatAndMalformedJson() {
        expectFailure("""{"format":"callguard-spam","version":99,"numbers":[]}""")
        expectFailure("{}")
        expectFailure("not json")
    }

    @Test fun rejectsOversizedFiles() {
        expectFailure(" ".repeat(SpamBackupCodec.MAX_BYTES + 1))
    }

    private fun expectFailure(json: String) {
        try {
            SpamBackupCodec.decode(json)
            fail("Invalid backup must not be accepted")
        } catch (_: IllegalArgumentException) {
        } catch (_: com.google.gson.JsonParseException) {
        } catch (_: ArithmeticException) {
        }
    }
}
