package com.inktone.data.mapper

import com.inktone.domain.model.PositionConflict
import com.inktone.domain.model.ReadingPositionSnapshot
import com.inktone.domain.valueobject.Locator
import com.inktone.infrastructure.database.entity.PendingConflictEntity

fun PositionConflict.toEntity(): PendingConflictEntity = PendingConflictEntity(
    publicationId = publicationId, bookTitle = bookTitle,
    localResourceHref = local.locator.resourceHref, localChapterIndex = local.locator.chapterIndex,
    localParagraphIndex = local.locator.paragraphIndex, localCharOffset = local.locator.charOffset,
    localDeviceLabel = local.deviceLabel, localAt = local.at, localChapterCount = local.chapterCount,
    remoteResourceHref = remote.locator.resourceHref, remoteChapterIndex = remote.locator.chapterIndex,
    remoteParagraphIndex = remote.locator.paragraphIndex, remoteCharOffset = remote.locator.charOffset,
    remoteDeviceLabel = remote.deviceLabel, remoteAt = remote.at, remoteChapterCount = remote.chapterCount,
)

fun PendingConflictEntity.toDomain(): PositionConflict = PositionConflict(
    publicationId = publicationId, bookTitle = bookTitle,
    local = ReadingPositionSnapshot(
        locator = Locator(localResourceHref, localChapterIndex, localParagraphIndex, localCharOffset),
        deviceLabel = localDeviceLabel, at = localAt, chapterIndex = localChapterIndex, chapterCount = localChapterCount,
    ),
    remote = ReadingPositionSnapshot(
        locator = Locator(remoteResourceHref, remoteChapterIndex, remoteParagraphIndex, remoteCharOffset),
        deviceLabel = remoteDeviceLabel, at = remoteAt, chapterIndex = remoteChapterIndex, chapterCount = remoteChapterCount,
    ),
)
