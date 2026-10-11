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
        val photo = RecoverableFile("photo", "content://test/photo", "photo.jpg", "image/jpeg", 24, 0, "Pictures/.hidden", RecoveryFileType.Photo,
            setOf(RecoveryFileSource.Hidden), true, RecoveryFileHiddenReason.HiddenDirectory, false, null)
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
        assertNull(vm.uiState.value.selectedCategory)
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
        runCurrent()
        assertEquals(0, repo.scans)
        vm.selectDeepCategory(RecoveryCategory.Videos)
        vm.startScan(); runCurrent()
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Videos, repo.category)
    }

    @Test fun allMediaScanAcceptsAudioOnlyPermission() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle())
        vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(audioAccess = true)))
        runCurrent()
        assertEquals(1, repo.scans)
        assertNull(repo.category)
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

    @Test fun quickAudioScanStartsOnceWithOnlyFolderAccess() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Audio")))
        val access = DeviceScanAccessState(DeviceScanPermissions(safFolderCount = 1))
        vm.onAccessStateChanged(access); runCurrent()
        vm.onAccessStateChanged(access); runCurrent()
        assertTrue(vm.uiState.value.canStartFullDeviceScan)
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Audio, repo.category)
    }

    @Test fun quickVideoScanStartsOnceWithOnlyFolderAccess() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Videos")))
        val access = DeviceScanAccessState(DeviceScanPermissions(safFolderCount = 1))
        vm.onAccessStateChanged(access); runCurrent()
        vm.onAccessStateChanged(access); runCurrent()
        assertTrue(vm.uiState.value.canStartFullDeviceScan)
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Videos, repo.category)
    }

    @Test fun quickPhotoScanStartsOnceWithOnlyFolderAccess() = runTest(dispatcher) {
        val repo = Repository()
        val vm = FullDeviceScanViewModel(repo, Premium(false), SavedStateHandle(mapOf(AppDestination.FullDeviceScan.ArgCategory to "Photos")))
        val access = DeviceScanAccessState(DeviceScanPermissions(safFolderCount = 1))
        vm.onAccessStateChanged(access); runCurrent()
        vm.onAccessStateChanged(access); runCurrent()
        assertTrue(vm.uiState.value.canStartFullDeviceScan)
        assertEquals(1, repo.scans)
        assertEquals(RecoveryCategory.Photos, repo.category)
        assertTrue(DeviceScanPermissions(safFolderCount = 1).canScan(null, RecoveryScanMode.Quick))
    }

    @Test fun olderResultsExcludeGalleryPhotosWithoutRetargetingSavedSelections() = runTest(dispatcher) {
        val repo = Repository()
        val gallery = repo.photo.copy(id = "gallery", relativePath = "DCIM/Camera", sources = emptySet(), isHidden = false, hiddenReason = null, dateModifiedMillis = 1)
        val video = gallery.copy(id = "video", fileType = RecoveryFileType.Video)
        val audio = gallery.copy(id = "audio", fileType = RecoveryFileType.Audio)
        repo.session = repo.session.copy(files = listOf(gallery, repo.photo, video, audio))
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session", "selected_indices" to intArrayOf(0)))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(setOf("photo"), vm.uiState.value.allFiles.map { it.id }.toSet())
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        vm.onToggleSelection("photo")
        val restored = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(setOf("photo"), restored.uiState.value.selectedIds)
    }

    @Test fun photoPolicySessionsDropGalleryVideosWithoutRetargetingPositions() = runTest(dispatcher) {
        val repo = Repository()
        val hidden = repo.photo.copy(id = "hidden_video", fileType = RecoveryFileType.Video, displayName = "clip.mp4", mimeType = "video/mp4", dateModifiedMillis = 1)
        val gallery = hidden.copy(id = "gallery_video", relativePath = "Movies", sources = emptySet(), isHidden = false, hiddenReason = null, dateModifiedMillis = 2)
        repo.session = repo.session.copy(selectedCategory = RecoveryCategory.Videos, files = listOf(gallery, hidden))
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session",
            "photo_result_policy" to 1, "selected_indices" to intArrayOf(0)))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(listOf("hidden_video"), vm.uiState.value.allFiles.map { it.id })
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        vm.onToggleSelection("hidden_video")
        val restored = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(setOf("hidden_video"), restored.uiState.value.selectedIds)
    }

    @Test fun photoPolicySelectionsRemainWhenNoVideoWasRemoved() = runTest(dispatcher) {
        val repo = Repository()
        val gallery = repo.photo.copy(id = "gallery_photo", relativePath = "DCIM/Camera", sources = emptySet(), isHidden = false, hiddenReason = null)
        repo.session = repo.session.copy(files = listOf(gallery, repo.photo))
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session",
            "photo_result_policy" to 1, "selected_indices" to intArrayOf(0)))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(setOf("photo"), vm.uiState.value.selectedIds)
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

    @Test fun existingAllFilesAccessAuthorizesBothAudioScanModesWithoutMediaPermissions() = runTest(dispatcher) {
        listOf(RecoveryScanMode.Quick, RecoveryScanMode.Deep).forEach { mode ->
            val repo = Repository()
            val vm = FullDeviceScanViewModel(repo, Premium(true), SavedStateHandle(mapOf(
                AppDestination.FullDeviceScan.ArgCategory to "Audio", AppDestination.FullDeviceScan.ArgMode to mode.name)))
            vm.onAccessStateChanged(DeviceScanAccessState(DeviceScanPermissions(allFilesAccess = true, sharedStorageTraversalAccess = true)))
            runCurrent()
            if (mode == RecoveryScanMode.Deep) { vm.startScan(); runCurrent() }
            assertEquals(1, repo.scans)
            assertEquals(RecoveryCategory.Audio, repo.category)
        }
    }

    @Test fun visualPolicySessionsDropOrdinaryAudioWithoutRetargetingSelections() = runTest(dispatcher) {
        val repo = Repository()
        val hidden = repo.photo.copy(id = "hidden_audio", fileType = RecoveryFileType.Audio,
            displayName = "recording.m4a", mimeType = "audio/mp4", dateModifiedMillis = 1)
        val library = hidden.copy(id = "library_audio", relativePath = "Music", sources = emptySet(),
            isHidden = false, hiddenReason = null, dateModifiedMillis = 2)
        repo.session = repo.session.copy(selectedCategory = RecoveryCategory.Audio, files = listOf(library, hidden))
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session",
            "visual_result_policy" to 1, "selected_indices" to intArrayOf(0)))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(listOf("hidden_audio"), vm.uiState.value.allFiles.map { it.id })
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        vm.onToggleSelection("hidden_audio")
        val restored = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(setOf("hidden_audio"), restored.uiState.value.selectedIds)
    }

    @Test fun visualPolicySelectionsRemainWhenNoAudioWasRemoved() = runTest(dispatcher) {
        val repo = Repository()
        val gallery = repo.photo.copy(id = "gallery_photo", relativePath = "DCIM/Camera", sources = emptySet(), isHidden = false, hiddenReason = null)
        repo.session = repo.session.copy(files = listOf(gallery, repo.photo))
        val state = SavedStateHandle(mapOf(AppDestination.FullRecoveryResults.ArgSessionId to "session",
            "visual_result_policy" to 1, "selected_indices" to intArrayOf(0)))
        val vm = FullRecoveryResultsViewModel(state, repo, Premium(true))
        runCurrent()
        assertEquals(setOf("photo"), vm.uiState.value.selectedIds)
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
        runCurrent()
        assertEquals(10_000, vm.uiState.value.totalFound)
        assertEquals(10_000, vm.uiState.value.visibleFiles.size)
        vm.selectVisible()
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
