package com.inktone.domain.usecase

import com.inktone.domain.repository.PublicationRepository

class ToggleFavoriteUseCase(
    private val publicationRepository: PublicationRepository,
) {
    suspend operator fun invoke(publicationId: String, isFavorite: Boolean) {
        publicationRepository.setFavorite(publicationId, isFavorite)
    }
}
