package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    private lateinit var imageListAdapter: ImageListAdapter
    private val imagePaths = mutableListOf<String>()
    private lateinit var progressBar: ProgressBar
    // android 13 兼容
    private val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // User allow the permission.
        } else {
            // User deny the permission.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        progressBar = findViewById(R.id.progressBar)
        // 假设您预先知道将要扫描的图片总数，或者您可以稍后设置它
        progressBar.max = 100 // 假设的最大值
        progressBar.progress = 0

        imageListAdapter = ImageListAdapter(imagePaths)
        findViewById<RecyclerView>(R.id.recyclerView_images).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = imageListAdapter
        }

        findViewById<Button>(R.id.button_scan).setOnClickListener {
            imagePaths.clear() // 清空现有的图片路径列表
            imageListAdapter.notifyDataSetChanged() // 通知适配器数据已经改变
            progressBar.visibility = View.VISIBLE // 显示进度条
            checkPermissionAndLoadImages() // 开始扫描图片
        }
    }

    private fun checkPermissionAndLoadImages() {
        
        // 未授权
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                // android 13 需要获取 image 权限
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            }
            return
        }
        
        // 已授权文件权限
        CoroutineScope(Dispatchers.Main).launch {
            loadImages()
        }
    }

    /**
     * 请求权限结果
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) { // 本次请求的code码结果
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                CoroutineScope(Dispatchers.Main).launch {
                    loadImages()
                }
            } else {
                // 无权限继续请求
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }

    private suspend fun loadImages() {

        val totalImages = countImages() // 获取图片总数
        withContext(Dispatchers.Main) {
            progressBar.max = totalImages // 设置进度条的最大值
            progressBar.progress = 0 // 初始化进度
        }

        withContext(Dispatchers.IO) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)

            imagePaths.clear()

            var i = 0
            cursor?.use {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    //todo
                    val imagePath = cursor.getString(columnIndex)
                    Log.d("ImageScanner", "Scanned image: $imagePath")
                    imagePaths.add(cursor.getString(columnIndex))
                    // 更新进度条
                    i++
                    val progress = i
                    withContext(Dispatchers.Main) {
                        progressBar.progress = progress
                    }
                }

            }

            // 扫描完成后，确保在主线程中隐藏进度条
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
            }

            imageListAdapter.notifyDataSetChanged() // 更新适配器
        }
    }

    private suspend fun countImages(): Int {
        return withContext(Dispatchers.IO) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            val count = cursor?.count ?: 0
            cursor?.close()
            count
        }
    }
}
