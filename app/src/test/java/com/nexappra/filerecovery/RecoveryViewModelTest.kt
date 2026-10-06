package com.nexappra.filerecovery

import androidx.lifecycle.SavedStateHandle
import com.nexappra.filerecovery.domain.model.*
import com.nexappra.filerecovery.domain.repository.*
import com.nexappra.filerecovery.presentation.scan.*
import com.nexappra.filerecovery.presentation.navigation.AppDestination
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private class Premium(active: Boolean) : PremiumRepository {
        override val state = MutableStateFlow(BillingState(access = PremiumAccess(active, Long.MAX_VALUE)))
        override fun refresh() {}
    }
    private class Repository : RecoveryScanRepository {
        var scans = 0; var copies = 0
        var category: RecoveryCategory? = null
        var fail = false
        val photo = RecoverableFile("photo", "content://test/photo", "photo.jpg", "image/jpeg", 24, 0, null, RecoveryFileType.Photo, emptySet(), false, null, false, null)
        var session = RecoveryScanSession("session", RecoveryScanMode.Quick, durationMillis = 10, totalBytesScanned = 24, files = listOf(photo), locations = emptyList(), completedAtMillis = 0)
        override suspend fun performFullDeviceScan(selectedCategory: RecoveryCategory?, mode: RecoveryScanMode, onSnapshot: suspend (RecoveryScanSnapshot) -> Unit): RecoveryScanSession {
            scans++; category = selectedCategory; return session
        }
        override suspend fun getSession(sessionId: String) = session
        override suspend fun recoverToFolder(sessionId: String, fileIds: Set<String>, treeUriString: String): RecoveryCopyResult {
            copies++; if (fail) error("Destination unavailable"); return RecoveryCopyResult(fileIds.size, 0)
        }
        override suspend fun exportArchive(sessionId: String, fileIds: Set<String>, documentUri: String) = recoverToFolder(sessionId, fileIds, documentUri)
        override suspend fun repairPhoto(sessionId: String, fileId: String, documentUri: String) = recoverToFolder(sessionId, setOf(fileId), documentUri)
    }
    @Test fun photoOnlyPermissionStartsPhotoScanOnce() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Photos")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullImagesAccess = true)))
        runCurrent()
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullImagesAccess = true)))
        runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Photos, repo.category)
    }
    @Test fun selectedPhotoPermissionStartsPhotoScanWithoutFullLibraryAccess() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Photos")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(partialVisualAccess = true)))
        runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Photos, repo.category)
    }

    @Test fun deepCategoryChoiceRestoresAndAudioNeedsOnlyFolderAccess() = runTest(dispatcher) {
        val repo = Repository()
        val saved = SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgMode to "Deep"))
        val vm = FullDeviceScanViewModel(repo, Premium(true), saved)
        assertEquals(RecoveryCategory.Photos, vm.uiState.value.selectedCategory)
        vm.selectDeepCategory(RecoveryCategory.Audio)
        val restored = FullDeviceScanViewModel(repo, Premium(true), saved)
        assertEquals(RecoveryCategory.Audio, restored.uiState.value.selectedCategory)
        restored.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(safFolderCount = 1)))
        restored.startScan(); runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Audio, repo.category)
        restored.selectDeepCategory(RecoveryCategory.Videos)
        assertEquals(RecoveryCategory.Audio, restored.uiState.value.selectedCategory)
    }
    @Test fun videoPermissionDoesNotAuthorizePhotoScanAndFreeCannotDeepScan() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Photos")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullVideosAccess = true)))
        runCurrent()
        assertEquals(0, repo.scans)
        val deep = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgMode to "Deep")))
        deep.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullImagesAccess = true)))
        deep.startScan(); runCurrent()
        assertEquals(0, repo.scans)
    }

    @Test fun deepScanStartsWithSelectedMediaAccessWithoutRequiringFolder() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(true), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgMode to "Deep")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullImagesAccess = true, fullVideosAccess = true, partialVisualAccess = true)))
        assertTrue(vm.uiState.value.canStartFullDeviceScan)
        vm.startScan(); runCurrent()
        assertEquals(1, repo.scans)
    }

    @Test fun deepScanRequiresAccessForItsSelectedCategory() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(true), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgMode to "Deep")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullImagesAccess = true)))
        vm.selectDeepCategory(RecoveryCategory.Videos)
        assertFalse(vm.uiState.value.canStartFullDeviceScan)
        vm.startScan(); runCurrent()
        assertEquals(0, repo.scans)
        vm.selectDeepCategory(RecoveryCategory.Audio)
        assertFalse(vm.uiState.value.canStartFullDeviceScan)
        vm.startScan(); runCurrent()
        assertEquals(0, repo.scans)
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(audioAccess = true)))
        assertTrue(vm.uiState.value.canStartFullDeviceScan)
        vm.startScan(); runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Audio, repo.category)
    }

    @Test fun folderAccessDoesNotAuthorizeQuickMediaScan() = runTest(dispatcher) {
        listOf(RecoveryCategory.Photos, RecoveryCategory.Videos, RecoveryCategory.Audio).forEach { category ->
            val repo = Repository()
            val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to category.name)))
            vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(safFolderCount = 1)))
            runCurrent()
            assertFalse(vm.uiState.value.canStartFullDeviceScan)
            assertEquals(0, repo.scans)
        }
    }

    @Test fun selectedVideoAccessStartsVideoScanAndNeverAudioScan() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Videos")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(partialVisualAccess = true)))
        runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Videos, repo.category)
        assertFalse(DeviceScanPermissions(partialVisualAccess = true).canReadMedia(RecoveryCategory.Audio))
    }

    @Test fun audioScanNeedsAudioAccessAndKeepsItsCategory() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Audio")))
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(fullImagesAccess = true, fullVideosAccess = true, partialVisualAccess = true)))
        runCurrent()
        assertEquals(0, repo.scans)
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(audioAccess = true)))
        runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Audio, repo.category)
    }
    @Test fun freeRecoveryIsBlockedAndSelectionSurvives() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullRecoveryResultsViewModel(SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session")), repo, Premium(false))
        runCurrent(); vm.onToggleSelection("photo"); vm.recoverSelectedTo("content://folder"); runCurrent()
        assertEquals(0, repo.copies)
        assertEquals(setOf("photo"), vm.uiState.value.selectedIds)
        assertEquals(FullRecoveryResultsEvent.OpenPremium, vm.events.first())
    }
    @Test fun failedCopyKeepsSelectionAndSuccessfulRetryClearsIt() = runTest(dispatcher) {
        val repo = Repository()
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session", "selected_ids" to arrayListOf("photo")))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent(); assertEquals(24L, vm.uiState.value.selectedTotalSizeBytes)
        repo.fail = true; vm.recoverSelectedTo("content://folder"); runCurrent()
        assertEquals(setOf("photo"), vm.uiState.value.selectedIds)
        assertFalse(vm.uiState.value.isRecovering)
        repo.fail = false; vm.recoverSelectedTo("content://folder"); runCurrent()
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        assertEquals(2, repo.copies)
    }

    @Test fun largeSelectionUsesCompactStateAndRestoresAfterProcessRecreation() = runTest(dispatcher) {
        val repo = Repository()
        repo.session = repo.session.copy(files = (0 until 10_000).map { index ->
            repo.photo.copy(id = "content://test/" + "long-folder-name/".repeat(15) + index, dateModifiedMillis = index.toLong())
        })
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session"))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent(); vm.selectVisible()
        assertEquals(10_000, vm.uiState.value.selectedIds.size)
        assertNull(state.get<ArrayList<String>>("selected_ids"))
        val indices = checkNotNull(state.get<IntArray>("selected_indices"))
        assertEquals(40_000, indices.size * Int.SIZE_BYTES)
        val recreatedState = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session", "selected_indices" to indices))
        val restored = FullRecoveryResultsViewModel(recreatedState, repo, Premium(true))
        runCurrent()
        assertEquals(vm.uiState.value.selectedIds, restored.uiState.value.selectedIds)
    }

    @Test fun pickerResultCanRestoreCompactSelectionBeforeUiLoad() = runTest(dispatcher) {
        val repo = Repository()
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session", "selected_indices" to intArrayOf(0)))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        vm.recoverSelectedTo("content://folder")
        runCurrent()
        assertEquals(1, repo.copies)
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
    }
}
