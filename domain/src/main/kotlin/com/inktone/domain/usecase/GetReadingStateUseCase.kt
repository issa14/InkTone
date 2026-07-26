package com.inktone.domain.usecase

import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.ReadingStateRepository

class GetReadingStateUseCase(
    private val readingStateRepository: ReadingStateRepository,
) {
    suspend operator fun invoke(publicationId: String): ReadingState? =
        readingStateRepository.get(publicationId)
}
