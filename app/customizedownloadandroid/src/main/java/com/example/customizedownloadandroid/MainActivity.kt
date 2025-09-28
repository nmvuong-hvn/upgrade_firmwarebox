package com.example.customizedownloadandroid

import android.os.Bundle
import android.util.Log
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.customizedownloadandroid.customizedownloading.DownloadEntity
import com.example.customizedownloadandroid.customizedownloading.FileStorage
import com.example.customizedownloadandroid.customizedownloading.MyDownloadManager
import com.example.customizedownloadandroid.ui.theme.UpgradeFirmwareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UpgradeFirmwareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val TAG = "Greeting"
    var dataLink by remember { mutableStateOf("") }
    val firmwareManager = remember { MyDownloadManager(context) }
    var downloadId by remember {
        mutableLongStateOf(-1L)
    }
    val dirPath= remember {
            FileStorage.getRootDirPath(context)
    }

//    val callback = remember { object : TestDownloadingManager1.DownloadingCallback {
//        override fun onProgress(
//            data: String,
//            downloadedBytes: Long,
//            totalBytes: Long,
//            downloadId: Long
//        ) {
//            Log.d(TAG, "onProgress: ====> data = $data - downloadId = $downloadId")
//        }
//
//        override fun onCurrentState(data: String, downloadId: Long) {
//        }
//
//    }}

//    LaunchedEffect(Unit) {
//        TestDownloadingManager1.getInstance().init()
//        TestDownloadingManager1.getInstance().setCallback(callback)
//    }
//    LaunchedEffect(downloadId) {
//        if (downloadId != -1L) {
//            firmwareManager.monitorDownloading()
//        }
//    }

    Scaffold (modifier = Modifier.fillMaxSize()){ paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            OutlinedTextField(dataLink, onValueChange = { dataLink = it }, modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(30.dp))
            Row (modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically){
                Button(onClick = {

                    val entity = DownloadEntity(
                        downloadId = System.currentTimeMillis(),
                        url = dataLink,
                        dirPath = dirPath,
                        filePath = "",
                        tempPath = "",
                        fileName = URLUtil.guessFileName(dataLink, null, null),
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        status = DownloadEntity.STATUS_DOWNLOADING
                    )

                    downloadId = firmwareManager.downloadFileWithRetrofit(entity)
                }) {
                    Text(text = "Downloading")
                }
                Button(onClick = {
                    firmwareManager.pauseDownloadingWithRetrofit(downloadId)
                }) {
                    Text(text = "Pause")
                }
                Button(onClick = {
                    firmwareManager.pauseDownloadingWithRetrofit(downloadId)
                }) {
                    Text(text = "Resume")
                }
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UpgradeFirmwareTheme {
        Greeting("Android")
    }
}