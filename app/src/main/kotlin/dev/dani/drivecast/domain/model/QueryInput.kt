package dev.dani.drivecast.domain.model

import dev.dani.drivecast.domain.usecase.SearchPlacesUseCase

data class QueryInput(
    val text: String = "",
    val suppressSearch: Boolean = false,
    val token: Long = 0L
) {
    val isSearchable: Boolean
        get() = suppressSearch.not() && text.length >= SearchPlacesUseCase.MIN_QUERY_LENGTH

    val dedupKey: Any get() = if (suppressSearch) token else text
}