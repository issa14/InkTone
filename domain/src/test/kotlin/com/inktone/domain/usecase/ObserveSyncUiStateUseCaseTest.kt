package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakeSyncAccountRepository
import com.inktone.core.testing.fake.FakeSyncOperationTracker
import com.inktone.domain.model.SyncAccount
import com.inktone.domain.model.SyncProviderId
import com.inktone.domain.model.SyncUiState
import com.inktone.domain.service.SyncOperation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lot 11, tâche 11.2 — la règle de priorité entre compte persisté et opération éphémère n'est écrite qu'ici, pas dans chaque écran. */
class ObserveSyncUiStateUseCaseTest {

    private fun account() = SyncAccount(
        provider = SyncProviderId.GOOGLE_DRIVE, accountLabel = "issa@example.com", linkedAt = 0L,
    )

    @Test
    fun aucun_compte_et_aucune_operation_rend_Unconfigured() = runTest {
        val useCase = ObserveSyncUiStateUseCase(FakeSyncAccountRepository(), FakeSyncOperationTracker())

        assertTrue(useCase().first() is SyncUiState.Unconfigured)
    }

    @Test
    fun un_compte_lie_sans_operation_rend_Configured() = runTest {
        val accountRepository = FakeSyncAccountRepository()
        accountRepository.save(account())
        val useCase = ObserveSyncUiStateUseCase(accountRepository, FakeSyncOperationTracker())

        assertTrue(useCase().first() is SyncUiState.Configured)
    }

    @Test
    fun une_authentification_en_cours_prime_meme_sans_compte_lie() = runTest {
        val operationTracker = FakeSyncOperationTracker()
        operationTracker.begin(SyncOperation.AUTHENTICATING)
        val useCase = ObserveSyncUiStateUseCase(FakeSyncAccountRepository(), operationTracker)

        assertTrue(useCase().first() is SyncUiState.Authenticating)
    }

    @Test
    fun une_synchronisation_en_cours_sur_un_compte_lie_rend_Syncing() = runTest {
        val accountRepository = FakeSyncAccountRepository()
        accountRepository.save(account())
        val operationTracker = FakeSyncOperationTracker()
        operationTracker.begin(SyncOperation.SYNCING)
        val useCase = ObserveSyncUiStateUseCase(accountRepository, operationTracker)

        assertTrue(useCase().first() is SyncUiState.Syncing)
    }
}
