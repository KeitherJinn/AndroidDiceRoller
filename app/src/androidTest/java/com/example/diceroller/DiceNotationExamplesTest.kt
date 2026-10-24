package com.example.diceroller

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DiceNotationExamplesTest {
    @Test fun englishAndChineseHelpKeepCompleteParseableExamples() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedText = listOf("d20", "2d6+3", "4d8-2")
        val expectedDice = listOf(DiceExpression(1, 20, 0), DiceExpression(2, 6, 3), DiceExpression(4, 8, -2))
        // Keep operators inside each token, so typographic dashes cannot silently become valid prefixes.
        val expressionToken = Regex("""[0-9]*[dD][0-9]+[^\s,，、.。:：;；]*""")
        for (locale in listOf(Locale.US, Locale.SIMPLIFIED_CHINESE)) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val resources = context.createConfigurationContext(configuration).resources
            for (resourceId in listOf(R.string.notation_hint, R.string.error_notation)) {
                val rendered = resources.getString(resourceId, *DiceNotationExamples.values.toTypedArray())
                val examples = expressionToken.findAll(rendered).map { it.value }.toList()
                assertEquals("Examples in $locale: $rendered", expectedText, examples)
                assertEquals("Parsed examples in $locale: $rendered", expectedDice, examples.map { parseDiceNotation(it) })
            }
        }
    }
}
