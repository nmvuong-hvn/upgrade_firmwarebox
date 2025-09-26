package com.marusys.downloadsdk

import android.content.Context
import android.util.Log
import android.util.SparseArray
import android.webkit.URLUtil
import com.marusys.downloadsdk.db.DatabaseCache
import com.marusys.downloadsdk.model.DownloadRequestFileModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Objects

class DownloadSDK (val context: Context) : NetworkManager.NetworkConnectionState{

    private val TAG = "DownloadSDK"
    private val lock = Any()
    private val downloadsMap  = mutableMapOf<String, DownloadRequestFileModel>()
    private val activesDownloadMap = mutableMapOf<String, DownloadTaskRun>()
    private val scope = CoroutineScope(Dispatchers.IO)
    interface DownloadState {
        fun onProgress(progress: Float)
        fun onChangedState(state : Int)
    }
    private val networkManager = NetworkManager(context, this)

    fun initialize(){
        DatabaseCache.create(context = context)
        networkManager.init()
        runBlocking {
            DatabaseCache.getInstance().getDataRequestDao().getAllDataRequestFailure()
        }
    }

    fun retryAndDownloadFile(url: String){
        val downloadRequest = downloadsMap[url]
        if (downloadRequest == null){
            val fileName = URLUtil.guessFileName(url, null, null)
            val downloadRequestFileModel = DownloadRequestFileModel(
                downloadId = System.currentTimeMillis(),
                url = url,
                fileName = fileName,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                state = 1
            )
            downloadsMap.put(url, downloadRequestFileModel)
            val downloadTaskRun = DownloadTaskRun(downloadRequestFileModel)
            downloadTaskRun.runTask()
            activesDownloadMap.put(url, downloadTaskRun)
        }else {
            if (downloadRequest.state == 3 || downloadRequest.state == 0 || downloadRequest.downloadedBytes > 0){
                // retry download

            } else {
                // already downloading
                Log.d(TAG, "downloadFile: =====> already downloading url = $url")
                return
            }
        }
    }

    override fun onConnected() {
        scope.launch {
            val dataRequestList = DatabaseCache.getInstance().getDataRequestDao().getAllDataRequestFailure()
            Log.d(TAG, "onConnected: =====> dataRequest = ${dataRequestList.size}")
            if (dataRequestList.isNotEmpty()) {
                dataRequestList.forEach { dataRequest ->
                    DatabaseCache.getInstance().getDataRequestDao().updateStateDataRequest(downloadId = dataRequest.downloadId, 0)
                }
            }
        }
    }

    override fun onDisconnected() {
        scope.launch {
            val dataRequestList =
                DatabaseCache.getInstance().getDataRequestDao().getAllDataRequest()
            Log.d(TAG, "onDisconnected: =====> dataRequest = ${dataRequestList.size}")
            if (dataRequestList.isNotEmpty()) {
                dataRequestList.forEach { dataRequest ->
                    DatabaseCache.getInstance().getDataRequestDao()
                        .updateStateDataRequest(downloadId = dataRequest.downloadId, 3)
                }
            }
        }

    }
}


