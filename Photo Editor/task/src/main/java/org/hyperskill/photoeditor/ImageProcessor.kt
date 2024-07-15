package org.hyperskill.photoeditor

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Color.alpha
import android.graphics.drawable.BitmapDrawable
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import java.io.OutputStream
import kotlin.math.*

class ImageProcessor(private val activity: AppCompatActivity) {

    suspend fun adjustBrightness(bitmap: Bitmap, brightnessValue: Int): Bitmap {

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val red = (Color.red(pixel) + brightnessValue).coerceIn(0, 255)
                val green = (Color.green(pixel) + brightnessValue).coerceIn(0, 255)
                val blue = (Color.blue(pixel) + brightnessValue).coerceIn(0, 255)
                val newColor = Color.rgb(red, green, blue)
                bitmap.setPixel(x, y, newColor)
            }
        }
        return bitmap
    }

    suspend fun adjustSaturation(bitmap: Bitmap, saturationValue: Int): Bitmap {
        val alpha = (255 + saturationValue) / (255 - saturationValue).toDouble()

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)

                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val rgbAvg = (red + green + blue) / 3
                val newRed = (alpha * (red - rgbAvg) + rgbAvg).toInt().coerceIn(0, 255)
                val newGreen = (alpha * (green - rgbAvg) + rgbAvg).toInt().coerceIn(0, 255)
                val newBlue = (alpha * (blue - rgbAvg) + rgbAvg).toInt().coerceIn(0, 255)
                val newColor = Color.rgb(newRed, newGreen, newBlue)
                bitmap.setPixel(x, y, newColor)
            }
        }
        return bitmap
    }

    suspend fun adjustGamma(bitmap: Bitmap, gammaValue: Double): Bitmap {

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)

                val red = Color.red(pixel).toDouble()
                val green = Color.green(pixel).toDouble()
                val blue = Color.blue(pixel).toDouble()
                val coef = 255
                val newRed = (coef * ((red / coef).pow(gammaValue))).toInt()
                val newGreen = (coef * ((green / coef).pow(gammaValue))).toInt()
                val newBlue = (coef * ((blue / coef).pow(gammaValue))).toInt()
                val newColor = Color.rgb(newRed, newGreen, newBlue)
                bitmap.setPixel(x, y, newColor)
            }
        }
        return bitmap
    }

    suspend fun adjustContrast(bitmap: Bitmap, contrastValue: Int, brightnessValue: Int): Bitmap {
        val alpha = (255 + contrastValue) / (255 - contrastValue).toDouble()
        val withBrightness = adjustBrightness(bitmap, brightnessValue)
        val avgBrightness = calculateAverageBrightness(withBrightness)

        for (x in 0 until withBrightness.width) {
            for (y in 0 until withBrightness.height) {
                val pixel = withBrightness.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                val newRed = (alpha * (red - avgBrightness) + avgBrightness).toInt().coerceIn(0, 255)
                val newGreen = (alpha * (green - avgBrightness) + avgBrightness).toInt().coerceIn(0, 255)
                val newBlue = (alpha * (blue - avgBrightness) + avgBrightness).toInt().coerceIn(0, 255)
                val newColor = Color.rgb(newRed, newGreen, newBlue)
                withBrightness.setPixel(x, y, newColor)
            }
        }
        return withBrightness
    }

    private fun calculateAverageBrightness(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        var totalBrightness = 0L

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                val brightness = red + green + blue
                totalBrightness += brightness
            }
        }
        return (totalBrightness / (width * height * 3)).toInt()
    }

    fun requestStoragePermission(bitmap: Bitmap) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            saveImageToGallery(bitmap)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                MEDIA_REQUEST_CODE
            )
        }
    }

    fun saveImageToGallery(bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.ImageColumns.WIDTH, bitmap.width)
            put(MediaStore.Images.ImageColumns.HEIGHT, bitmap.height)
        }
        val resolver = activity.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return
        resolver.openOutputStream(uri).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }

    companion object {
        const val MEDIA_REQUEST_CODE = 0
    }
}