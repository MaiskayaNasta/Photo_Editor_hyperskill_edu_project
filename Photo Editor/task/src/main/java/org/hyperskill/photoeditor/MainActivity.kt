package org.hyperskill.photoeditor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.slider.Slider
import java.io.IOException
import java.io.InputStream
import com.google.android.material.slider.Slider.OnChangeListener
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var currentImage: ImageView
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var defaultBitmap: Bitmap
    private lateinit var brightnessSlider: Slider
    private lateinit var saturationSlider: Slider
    private lateinit var contrastSlider: Slider
    private lateinit var gammaSlider: Slider
    private var lastJob: Job? = null

    private val activityResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val photoUri = result.data?.data ?: return@registerForActivityResult
            currentImage.setImageURI(photoUri)
            defaultBitmap = currentImage.drawable.toBitmap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        val originalBitmap = createBitmap()
        currentImage.setImageBitmap(originalBitmap)
        defaultBitmap = currentImage.drawable.toBitmap()
        imageProcessor = ImageProcessor(this)

        val galleryButton = findViewById<Button>(R.id.btnGallery)
        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }
        val saveButton = findViewById<Button>(R.id.btnSave)
        saveButton.setOnClickListener {
            defaultBitmap.let { imageProcessor.requestStoragePermission(it) }
        }

        val updateFilters: () -> Unit = {
            val brightnessValue = brightnessSlider.value.toInt()
            val contrastValue = contrastSlider.value.toInt()
            val saturationValue = saturationSlider.value.toInt()
            val gammaValue = gammaSlider.value.toDouble()
            applyFilters(brightnessValue, contrastValue, saturationValue, gammaValue)
        }
        brightnessSlider.addOnChangeListener { _, _, _ -> updateFilters() }
        contrastSlider.addOnChangeListener { _, _, _ -> updateFilters() }
        saturationSlider.addOnChangeListener { _, _, _ -> updateFilters() }
        gammaSlider.addOnChangeListener { _, _, _ -> updateFilters() }
    }

    private fun applyFilters(brightnessValue: Int, contrastValue: Int, saturationValue: Int, gammaValue: Double) {
        lastJob?.cancel()
        lastJob = GlobalScope.launch(Dispatchers.Default) {

            val modifiedBitmap = defaultBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return@launch

            val brightenCopyDeferred: Deferred<Bitmap> = this.async {
                imageProcessor.adjustBrightness(modifiedBitmap, brightnessValue)
            }
            val brightenCopy: Bitmap = brightenCopyDeferred.await()

            var new = imageProcessor.adjustContrast(brightenCopy, contrastValue, 0)
            new = imageProcessor.adjustSaturation(new, saturationValue)
            new = imageProcessor.adjustGamma(new, gammaValue)

            ensureActive()
            runOnUiThread {
                currentImage.setImageBitmap(new)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ImageProcessor.MEDIA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                defaultBitmap.let { imageProcessor.saveImageToGallery(it) }
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindViews() {
        currentImage = findViewById(R.id.ivPhoto)
        brightnessSlider = findViewById(R.id.slBrightness)
        saturationSlider = findViewById(R.id.slSaturation)
        contrastSlider = findViewById(R.id.slContrast)
        gammaSlider = findViewById(R.id.slGamma)
    }

    private fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                index = y * width + x
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x + y) % 100 + 120

                pixels[index] = Color.rgb(R, G, B)
            }
        }
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }
}