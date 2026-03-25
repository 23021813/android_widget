package com.carlauncher.bridge.core

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.text.getSpans

private data class Span(
    val begin: Int,
    val end: Int,
    val style: Int = Typeface.NORMAL
)

object ParserHelper {
    data class SpanSplitResult(
        val text: String,
        val isKeySpan: Boolean
    )

    private fun findSpans(input: Spanned): List<Span> {
        val results = arrayListOf<Span>()
        var spanBegin = 0
        var spanEnd = 0
        val len = input.length
        while (spanEnd < len) {
            spanEnd = input.nextSpanTransition(spanBegin, len, StyleSpan::class.java)
            val spans = input.getSpans<StyleSpan>(spanBegin, spanEnd)
            results.add(
                Span(
                    begin = spanBegin,
                    end = spanEnd,
                    style = spans.firstOrNull()?.style ?: Typeface.NORMAL
                )
            )
            spanBegin = spanEnd
        }
        return results
    }

    fun splitByStyleSpan(
        input: Spanned,
        keyStyle: Int,
        minSpanLength: Int = 0
    ): ArrayList<SpanSplitResult> {
        val result = arrayListOf<SpanSplitResult>()
        val spans = findSpans(input)
        if (spans.isEmpty()) return result

        var begin = 0
        var previousSegmentMatched = false

        spans.forEachIndexed { index, span ->
            val segment = input.substring(span.begin, span.end)
            val segmentMatched =
                span.style == keyStyle && segment.trim().length >= minSpanLength

            if (segmentMatched != previousSegmentMatched) {
                val previousText = input.substring(begin, span.begin).trim()
                if (previousText.isNotEmpty()) {
                    result.add(SpanSplitResult(previousText, previousSegmentMatched))
                }
                begin = span.begin
            }

            if (index == spans.lastIndex) {
                val lastText = input.substring(begin, span.end).trim()
                if (lastText.isNotEmpty()) {
                    result.add(SpanSplitResult(lastText, segmentMatched))
                }
            }

            previousSegmentMatched = segmentMatched
        }

        return result
    }
}
