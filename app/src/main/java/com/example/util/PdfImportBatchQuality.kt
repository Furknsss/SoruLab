package com.example.util

/** A printed test is complete only if every source answer-key question exists once. */
internal object PdfImportBatchQuality {
    data class Item(val sourceTestId: String, val printedNumber: Int)

    fun invalidScopes(
        items: List<Item>,
        expectedByScope: Map<String, Set<Int>>
    ): Set<String> {
        val groups = items.filter { it.sourceTestId.isNotBlank() }
            .groupBy { it.sourceTestId }
        return groups.mapNotNull { (scope, questions) ->
            val actualNumbers = questions.map { it.printedNumber }
            val actualSet = actualNumbers.toSet()
            val expected = expectedByScope[scope].orEmpty()
            val isInvalid = actualNumbers.any { it <= 0 } ||
                actualSet.size != actualNumbers.size ||
                // A complete printed test normally starts at 1 and contains
                // no gaps, even when the publisher omits an answer table.
                (actualSet.isNotEmpty() && actualSet != (1..actualSet.max()).toSet()) ||
                (expected.isNotEmpty() && actualSet != expected)
            scope.takeIf { isInvalid }
        }.toSet()
    }
}
