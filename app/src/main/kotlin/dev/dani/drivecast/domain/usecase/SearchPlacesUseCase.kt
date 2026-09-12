package dev.dani.drivecast.domain.usecase

import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.domain.model.Place
import javax.inject.Inject

// Resolve text input into place suggestions
class SearchPlacesUseCase @Inject constructor(
    private val repository: DriveCastRepository
) {
    suspend operator fun invoke(query: String): List<Place> =
        if (query.trim().length < MIN_QUERY_LENGTH) emptyList() else repository.geocode(query)

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
