package com.gravityos.assistant.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(
    val name: String,
    val number: String,
    val score: Double
)

sealed class CallResolution {
    data class DirectCalled(val contact: ContactMatch) : CallResolution()
    data class AskConfirmation(val message: String, val candidates: List<ContactMatch>) : CallResolution()
    data class PermissionRequired(val message: String) : CallResolution()
    data class NotFound(val query: String) : CallResolution()
}

class ContactCallManager(private val context: Context) {

    private val relationAliases = mapOf(
        "mummy" to listOf("mummy", "mom", "maa", "mata", "mother", "mummi"),
        "mom" to listOf("mom", "mummy", "maa", "mother"),
        "maa" to listOf("maa", "mummy", "mom", "mother"),
        "papa" to listOf("papa", "dad", "father", "pitaji", "pappa", "bapu"),
        "dad" to listOf("dad", "papa", "father"),
        "bhai" to listOf("bhai", "bhaiya", "brother"),
        "bhaiya" to listOf("bhaiya", "bhai", "brother"),
        "didi" to listOf("didi", "di", "sister")
    )

    fun resolveAndCall(rawQuery: String): CallResolution {
        val cleaned = rawQuery.trim()
        if (cleaned.isEmpty()) return CallResolution.NotFound("")

        // Direct numeric phone number check
        val digitsOnly = cleaned.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.length >= 7 && cleaned.replace(Regex("[0-9+\\s-]"), "").isEmpty()) {
            val numberMatch = ContactMatch(name = digitsOnly, number = digitsOnly, score = 1.0)
            return placeCall(numberMatch)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return CallResolution.PermissionRequired(
                "Contacts read karne ki permission nahi mili hai. Setup me jakar Contacts permission allow karo."
            )
        }

        val allContacts = loadAllContacts()
        if (allContacts.isEmpty()) {
            return CallResolution.NotFound(cleaned)
        }

        val normalizedQuery = StringSimilarity.normalize(cleaned)
        val expandedQueries = relationAliases[normalizedQuery] ?: listOf(normalizedQuery)

        val scored = allContacts.mapNotNull { contact ->
            val bestScore = expandedQueries.maxOf { q -> StringSimilarity.score(q, contact.name) }
            if (bestScore >= 0.56) contact.copy(score = bestScore) else null
        }.sortedByDescending { it.score }

        if (scored.isEmpty()) {
            return CallResolution.NotFound(cleaned)
        }

        val top = scored.first()
        val closeMatches = scored.filter { it.score >= 0.78 }.take(4)

        // Multiple strong matches (e.g., "Rahul Sharma" and "Rahul Verma") -> Ask user
        if (closeMatches.size > 1) {
            val namesPreview = closeMatches.mapIndexed { idx, c -> "${idx + 1}. ${c.name}" }.joinToString("\n")
            return CallResolution.AskConfirmation(
                message = "\"$cleaned\" naam ke ${closeMatches.size} contacts mile hain. Kisko call lagau?\n$namesPreview",
                candidates = closeMatches
            )
        }

        // Single high-confidence match -> Direct Call!
        if (top.score >= 0.84) {
            return placeCall(top)
        }

        // Doubtful / partial match -> Ask confirmation before calling
        val suggestions = scored.take(3)
        return if (suggestions.size == 1) {
            CallResolution.AskConfirmation(
                message = "Mujhe thoda doubt hai — kya aap ${top.name} (${top.number}) ko call lagana chahte hain?",
                candidates = suggestions
            )
        } else {
            val listText = suggestions.mapIndexed { idx, c -> "${idx + 1}. ${c.name}" }.joinToString("\n")
            CallResolution.AskConfirmation(
                message = "Exact naam match nahi hua. Kya inme se kisi ko call lagau?\n$listText",
                candidates = suggestions
            )
        }
    }

    fun placeCall(contact: ContactMatch): CallResolution {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val telUri = Uri.parse("tel:${Uri.encode(contact.number)}")

        return if (hasCallPermission) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, telUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                CallResolution.DirectCalled(contact)
            } catch (e: Exception) {
                openDialerFallback(telUri)
                CallResolution.PermissionRequired("Direct call start nahi ho payi, dialer khol diya hai.")
            }
        } else {
            openDialerFallback(telUri)
            CallResolution.PermissionRequired(
                "Direct call ke liye Phone permission chahiye. Abhi ${contact.name} ka number dialer me khol diya hai."
            )
        }
    }

    private fun openDialerFallback(telUri: Uri) {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
        } catch (_: Exception) {
        }
    }

    private fun loadAllContacts(): List<ContactMatch> {
        val results = LinkedHashMap<String, ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val rawNumber = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isBlank() || rawNumber.isBlank()) continue

                    val cleanDigits = rawNumber.replace(Regex("[^0-9]"), "")
                    val lastTen = if (cleanDigits.length >= 10) cleanDigits.takeLast(10) else cleanDigits
                    val dedupeKey = "${name.lowercase()}_$lastTen"

                    if (!results.containsKey(dedupeKey)) {
                        results[dedupeKey] = ContactMatch(name = name, number = rawNumber, score = 0.0)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return results.values.toList()
    }
}
