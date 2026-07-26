package com.inktone.domain.usecase

import com.inktone.domain.repository.AnnotationRepository

class DeleteAnnotationUseCase(
    private val annotationRepository: AnnotationRepository,
) {
    suspend operator fun invoke(id: String) {
        annotationRepository.delete(id)
    }
}
