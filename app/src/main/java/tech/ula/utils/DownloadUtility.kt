package tech.ula.utils

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class DownloadUtility(val context: Context, val archType: String, distType: String) {

    companion object {
        val branch = "master"
    }

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    // Prefix file name with OS type to move it into the correct folder
    private val assets = arrayListOf(
            "support:proot" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/$archType/proot",
            "support:busybox" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/$archType/busybox",
            "support:libtalloc.so.2" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/$archType/libtalloc.so.2",
            "support:execInProot.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/all/execInProot.sh",
            "support:killProcTree.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/all/killProcTree.sh",
            "support:isServerInProcTree.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/core/all/isServerInProcTree.sh",
            "$distType:startSSHServer.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/all/startSSHServer.sh",
            "$distType:startVNCServer.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/all/startVNCServer.sh",
            "$distType:startVNCServerStep2.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/all/startVNCServerStep2.sh",
            "$distType:extractFilesystem.sh" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/all/extractFilesystem.sh",
            "$distType:busybox" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/$archType/busybox",
            "$distType:libdisableselinux.so" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/$archType/libdisableselinux.so",
            "$distType:ld.so.preload" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/all/ld.so.preload",
            "$distType:rootfs.tar.gz" to "https://github.com/CypherpunkArmory/UserLAnd-Assets/raw/$branch/distribution/${distType}/$archType/rootfs.tar.gz"
    )

    fun checkIfLargeRequirement(): Boolean {
        if(!isWifiEnabled()) {
            assets.forEach {
                (type, endpoint) ->
                if(type.contains("rootfs") && assetNeedsToUpdated(type, endpoint)) {
                    return true
                }
            }
        }
        return false
    }

    private fun download(type: String, url: String): Long {
        // TODO Dynamically adjust allowed network types to ensure no mobile use
        // Currently just assuming the dialog choices succeed in some way
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setDescription("Downloading $type.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UserLAnd:$type")
        deletePreviousDownload("UserLAnd:$type")
        return downloadManager.enqueue(request)
    }

    private fun assetNeedsToUpdated(type: String, endpoint: String): Boolean {
        val (subdirectory, filename) = type.split(":")
        val asset = File("${context.filesDir.path}/$subdirectory/$filename")
        val sharedPref = context.getSharedPreferences("file_date_stamps", Context.MODE_PRIVATE)
        val currentDateStamp = sharedPref.getLong(type, 0)
        val newDateStamp = urlDateModified(endpoint)
        if ((newDateStamp != 0L) && (currentDateStamp != newDateStamp)) {
            with (sharedPref.edit()) {
                putLong(type, newDateStamp)
                commit()
            }
            if (asset.exists())
                asset.delete()
        }
        return !asset.exists()
    }

    fun downloadRequirements(): List<Long> {
        return assets
                .filter {
                    (type, endpoint) ->
                    assetNeedsToUpdated(type, endpoint)
                }
                .map {
                    (type, endpoint) ->
                    download(type, endpoint)
                }
    }

    private fun isWifiEnabled(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        return if (connectivityManager is ConnectivityManager) {
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI
        } else false
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        return if (connectivityManager is ConnectivityManager) {
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected ?: false
        } else false
    }

    private fun deletePreviousDownload(type: String) {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadFile = File(downloadDirectory,type)
        if (downloadFile.exists())
            downloadFile.delete()
    }

    private fun urlDateModified(address: String): Long {
        val url = URL(address)
        val httpCon = url.openConnection() as HttpURLConnection
        return httpCon.lastModified
    }

}

