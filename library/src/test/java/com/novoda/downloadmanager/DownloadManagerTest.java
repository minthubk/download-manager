package com.novoda.downloadmanager;

import android.os.Handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;

import static com.google.common.truth.Truth.assertThat;
import static com.novoda.downloadmanager.DownloadBatchIdFixtures.aDownloadBatchId;
import static com.novoda.downloadmanager.DownloadFileIdFixtures.aDownloadFileId;
import static com.novoda.downloadmanager.DownloadFileStatusFixtures.aDownloadFileStatus;
import static com.novoda.downloadmanager.InternalDownloadBatchStatusFixtures.anInternalDownloadsBatchStatus;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class DownloadManagerTest {

    private static final InternalDownloadBatchStatus BATCH_STATUS = anInternalDownloadsBatchStatus().build();
    private static final InternalDownloadBatchStatus ADDITIONAL_BATCH_STATUS = anInternalDownloadsBatchStatus().build();
    private static final DownloadBatchId DOWNLOAD_BATCH_ID = aDownloadBatchId().withRawDownloadBatchId("id01").build();
    private static final DownloadBatchId ADDITIONAL_DOWNLOAD_BATCH_ID = aDownloadBatchId().withRawDownloadBatchId("id02").build();
    private static final Batch BATCH = new Batch.Builder(DOWNLOAD_BATCH_ID, "title").build();
    private static final DownloadFileId DOWNLOAD_FILE_ID = aDownloadFileId().withRawDownloadFileId("file_id_01").build();
    private static final DownloadFileStatus DOWNLOAD_FILE_STATUS = aDownloadFileStatus().withDownloadFileId(DOWNLOAD_FILE_ID).build();

    private final AllStoredDownloadsSubmittedCallback allStoredDownloadsSubmittedCallback = mock(AllStoredDownloadsSubmittedCallback.class);
    private final AllBatchStatusesCallback allBatchStatusesCallback = mock(AllBatchStatusesCallback.class);
    private final DownloadFileStatusCallback downloadFileStatusCallback = mock(DownloadFileStatusCallback.class);
    private final DownloadService downloadService = mock(DownloadService.class);
    private final Object lock = spy(new Object());
    private final ExecutorService executorService = mock(ExecutorService.class);
    private final Handler handler = mock(Handler.class);
    private final DownloadBatch downloadBatch = mock(DownloadBatch.class);
    private final DownloadBatch additionalDownloadBatch = mock(DownloadBatch.class);
    private final DownloadBatchCallback downloadBatchCallback = mock(DownloadBatchCallback.class);
    private final FileOperations fileOperations = mock(FileOperations.class);
    private final DownloadsBatchPersistence downloadsBatchPersistence = mock(DownloadsBatchPersistence.class);
    private final LiteDownloadManagerDownloader downloadManagerDownloader = mock(LiteDownloadManagerDownloader.class);

    private DownloadManager downloadManager;
    private Map<DownloadBatchId, DownloadBatch> downloadBatches = new HashMap<>();
    private List<DownloadBatchStatus> downloadBatchStatuses = new ArrayList<>();
    private List<DownloadBatchCallback> downloadBatchCallbacks = new ArrayList<>();
    private DownloadFileStatus downloadFileStatus = null;

    @Before
    public void setUp() {
        downloadBatches = new HashMap<>();
        downloadBatches.put(DOWNLOAD_BATCH_ID, downloadBatch);
        downloadBatches.put(ADDITIONAL_DOWNLOAD_BATCH_ID, additionalDownloadBatch);

        downloadBatchCallbacks.add(downloadBatchCallback);

        downloadManager = new DownloadManager(
                lock,
                executorService,
                handler,
                downloadBatches,
                downloadBatchCallbacks,
                fileOperations,
                downloadsBatchPersistence,
                downloadManagerDownloader
        );

        setupDownloadBatchesResponse();
        setupDownloadBatchStatusesResponse();
        setupDownloadStatusResponse();

        given(downloadBatch.status()).willReturn(BATCH_STATUS);
        given(additionalDownloadBatch.downloadFileStatusWith(DOWNLOAD_FILE_ID)).willReturn(DOWNLOAD_FILE_STATUS);
        given(additionalDownloadBatch.status()).willReturn(ADDITIONAL_BATCH_STATUS);

        willAnswer(invocation -> {
            ((Callable) invocation.getArgument(0)).call();
            return null;
        }).given(executorService).submit(any(Callable.class));

        willAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).given(handler).post(any(Runnable.class));
    }

    private void setupDownloadBatchesResponse() {
        willAnswer(invocation -> {
            DownloadsBatchPersistence.LoadBatchesCallback loadBatchesCallback = invocation.getArgument(1);
            loadBatchesCallback.onLoaded(Arrays.asList(downloadBatch, additionalDownloadBatch));
            return null;
        }).given(downloadsBatchPersistence).loadAsync(any(FileOperations.class), any(DownloadsBatchPersistence.LoadBatchesCallback.class));
    }

    private void setupDownloadBatchStatusesResponse() {
        willAnswer(invocation -> {
            downloadBatchStatuses = invocation.getArgument(0);
            return null;
        }).given(allBatchStatusesCallback).onReceived(ArgumentMatchers.anyList());
    }

    private void setupDownloadStatusResponse() {
        willAnswer(invocation -> {
            downloadFileStatus = invocation.getArgument(0);
            return null;
        }).given(downloadFileStatusCallback).onReceived(any(DownloadFileStatus.class));
    }

    @Test
    public void setDownloadService_whenInitialising() {
        downloadManager.initialise(downloadService);

        verify(downloadManagerDownloader).setDownloadService(downloadService);
    }

    @Test(timeout = 500)
    public void notifyAll_whenInitialising() throws InterruptedException {
        synchronized (lock) {
            Executors.newSingleThreadExecutor().submit(() -> downloadManager.initialise(downloadService));

            lock.wait();
        }
    }

    @Test
    public void triggersDownloadOfBatches_whenSubmittingAllStoredDownloads() {
        downloadManager.submitAllStoredDownloads(allStoredDownloadsSubmittedCallback);

        InOrder inOrder = inOrder(downloadManagerDownloader);
        inOrder.verify(downloadManagerDownloader).download(downloadBatch, downloadBatches);
        inOrder.verify(downloadManagerDownloader).download(additionalDownloadBatch, downloadBatches);
    }

    @Test
    public void notifies_whenSubmittingAllStoredDownloads() {
        downloadManager.submitAllStoredDownloads(allStoredDownloadsSubmittedCallback);

        verify(allStoredDownloadsSubmittedCallback).onAllDownloadsSubmitted();
    }

    @Test
    public void downloadGivenBatch() {
        downloadManager.download(BATCH);

        verify(downloadManagerDownloader).download(BATCH, downloadBatches);
    }

    @Test
    public void doesNotPause_whenBatchIdIsUnknown() {
        downloadManager.pause(new LiteDownloadBatchId("unknown"));

        verifyZeroInteractions(downloadBatch, additionalDownloadBatch);
    }

    @Test
    public void pausesBatch() {
        downloadManager.pause(DOWNLOAD_BATCH_ID);

        verify(downloadBatch).pause();
    }

    @Test
    public void doesNotResume_whenBatchIdIsUnknown() {
        downloadManager.pause(new LiteDownloadBatchId("unknown"));

        verifyZeroInteractions(downloadBatch, additionalDownloadBatch);
    }

    @Test
    public void doesNotResume_whenBatchIsAlreadyDownloading() {
        given(downloadBatch.status()).willReturn(anInternalDownloadsBatchStatus().withStatus(DownloadBatchStatus.Status.DOWNLOADING).build());

        downloadManager.resume(DOWNLOAD_BATCH_ID);

        InOrder inOrder = inOrder(downloadBatch);
        inOrder.verify(downloadBatch).status();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void removesBatchFromInternalList_whenResuming() {
        given(downloadBatch.status()).willReturn(anInternalDownloadsBatchStatus().build());

        downloadManager.resume(DOWNLOAD_BATCH_ID);

        assertThat(downloadBatches).doesNotContainEntry(DOWNLOAD_BATCH_ID, downloadBatch);
    }

    @Test
    public void resumesBatch() {
        given(downloadBatch.status()).willReturn(anInternalDownloadsBatchStatus().build());

        downloadManager.resume(DOWNLOAD_BATCH_ID);

        verify(downloadBatch).resume();
    }

    @Test
    public void triggersDownload_whenResumingBatch() {
        given(downloadBatch.status()).willReturn(anInternalDownloadsBatchStatus().build());

        downloadManager.resume(DOWNLOAD_BATCH_ID);

        verify(downloadManagerDownloader).download(downloadBatch, downloadBatches);
    }

    @Test
    public void doesNotDelete_whenBatchIdIsUnknown() {
        downloadManager.delete(new LiteDownloadBatchId("unknown"));

        verifyZeroInteractions(downloadBatch, additionalDownloadBatch);
    }

    @Test
    public void removesBatchFromInternalList_whenDeleting() {
        downloadManager.delete(DOWNLOAD_BATCH_ID);

        assertThat(downloadBatches).doesNotContainEntry(DOWNLOAD_BATCH_ID, downloadBatch);
    }

    @Test
    public void deletesBatch() {
        downloadManager.delete(DOWNLOAD_BATCH_ID);

        verify(downloadBatch).delete();
    }

    @Test
    public void addsCallbackToInternalList() {
        DownloadBatchCallback additionalDownloadBatchCallback = mock(DownloadBatchCallback.class);

        downloadManager.addDownloadBatchCallback(additionalDownloadBatchCallback);

        assertThat(downloadBatchCallbacks).contains(additionalDownloadBatchCallback);
    }

    @Test
    public void removesCallbackFromInternalList() {
        downloadManager.removeDownloadBatchCallback(downloadBatchCallback);

        assertThat(downloadBatchCallbacks).doesNotContain(downloadBatchCallback);
    }

    @Test(timeout = 500)
    public void waitsForServiceToExist_whenGettingAllBatchStatuses() {
        notifyLockOnAnotherThread();

        downloadManager.getAllDownloadBatchStatuses(allBatchStatusesCallback);

        assertThat(downloadBatchStatuses).containsExactly(BATCH_STATUS, ADDITIONAL_BATCH_STATUS);
    }

    @Test
    public void getsAllBatchStatuses_whenServiceAlreadyExists() {
        downloadManager.initialise(mock(DownloadService.class));

        downloadManager.getAllDownloadBatchStatuses(allBatchStatusesCallback);

        assertThat(downloadBatchStatuses).containsExactly(BATCH_STATUS, ADDITIONAL_BATCH_STATUS);
    }

    @Test(timeout = 500)
    public void waitsForServiceToExist_whenGettingAllBatchStatusesWithSynchronousCall() {
        notifyLockOnAnotherThread();

        List<DownloadBatchStatus> allDownloadBatchStatuses = downloadManager.getAllDownloadBatchStatuses();

        assertThat(allDownloadBatchStatuses).containsExactly(BATCH_STATUS, ADDITIONAL_BATCH_STATUS);
    }

    @Test
    public void getsAllBatchStatusesWithSynchronousCall_whenServiceAlreadyExists() {
        downloadManager.initialise(mock(DownloadService.class));

        List<DownloadBatchStatus> allDownloadBatchStatuses = downloadManager.getAllDownloadBatchStatuses();

        assertThat(allDownloadBatchStatuses).containsExactly(BATCH_STATUS, ADDITIONAL_BATCH_STATUS);
    }

    @Test(timeout = 500)
    public void waitsForServiceToExist_whenGettingDownloadStatusWithMatchingId() {
        notifyLockOnAnotherThread();

        downloadManager.getDownloadStatusWithMatching(DOWNLOAD_FILE_ID, downloadFileStatusCallback);

        assertThat(downloadFileStatus).isEqualTo(DOWNLOAD_FILE_STATUS);
    }

    @Test
    public void getsDownloadStatusMatchingId_whenServiceAlreadyExists() {
        downloadManager.initialise(mock(DownloadService.class));

        downloadManager.getDownloadStatusWithMatching(DOWNLOAD_FILE_ID, downloadFileStatusCallback);

        assertThat(downloadFileStatus).isEqualTo(DOWNLOAD_FILE_STATUS);
    }

    @Test(timeout = 500)
    public void waitsForServiceToExist_whenGettingDownloadStatusWithMatchingIdWithSynchronousCall() {
        notifyLockOnAnotherThread();

        DownloadFileStatus fileStatus = downloadManager.getDownloadStatusWithMatching(DOWNLOAD_FILE_ID);

        assertThat(fileStatus).isEqualTo(DOWNLOAD_FILE_STATUS);
    }

    @Test
    public void getsDownloadStatusMatchingIdWithSynchronousCall_whenServiceAlreadyExists() {
        downloadManager.initialise(mock(DownloadService.class));

        DownloadFileStatus fileStatus = downloadManager.getDownloadStatusWithMatching(DOWNLOAD_FILE_ID);

        assertThat(fileStatus).isEqualTo(DOWNLOAD_FILE_STATUS);
    }

    private void notifyLockOnAnotherThread() {
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    synchronized (lock) {
                        lock.notifyAll();
                    }
                });
    }

}
