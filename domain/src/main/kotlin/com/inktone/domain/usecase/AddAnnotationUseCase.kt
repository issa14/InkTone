package com.inktone.domain.usecase

import com.inktone.domain.model.Annotation
import com.inktone.domain.repository.AnnotationRepository

class AddAnnotationUseCase(
    private val annotationRepository: AnnotationRepository,
) {
    suspend operator fun invoke(annotation: Annotation) {
        annotationRepository.insert(annotation)
    }
}
