package com.example.dashboard_and_security_module

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.chrisbanes.photoview.PhotoView // Import PhotoView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfViewerActivity : AppCompatActivity() {

    // Change the type from ImageView to PhotoView
    private lateinit var pdfImageView: PhotoView

    private lateinit var pageNumberTextView: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        // Find the PhotoView by its ID
        pdfImageView = findViewById(R.id.pdf_image_view)
        pageNumberTextView = findViewById(R.id.tv_page_number)
        prevButton = findViewById(R.id.btn_previous_page)
        nextButton = findViewById(R.id.btn_next_page)

        val fileName = intent.getStringExtra("PDF_FILE_NAME")
        if (fileName != null) {
            try {
                openPdfRenderer(fileName)
                showPage(currentPageIndex)
            } catch (e: IOException) {
                Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }

        prevButton.setOnClickListener {
            if (currentPageIndex > 0) {
                showPage(currentPageIndex - 1)
            }
        }

        nextButton.setOnClickListener {
            if (pdfRenderer != null && currentPageIndex < pdfRenderer!!.pageCount - 1) {
                showPage(currentPageIndex + 1)
            }
        }
    }

    @Throws(IOException::class)
    private fun openPdfRenderer(fileName: String) {
        val tempFile = File(cacheDir, "temp_pdf.pdf")
        assets.open(fileName).use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
    }

    private fun showPage(index: Int) {
        // Close the previous page before opening a new one
        currentPage?.close()
        currentPage = pdfRenderer?.openPage(index).also { page ->
            currentPageIndex = index

            // --- KEY CHANGE FOR QUALITY ---
            // Render at a higher resolution for better quality when zooming.
            // A factor of 2 provides a good balance between quality and memory usage.
            val scaleFactor = 2f
            val bitmap = Bitmap.createBitmap(
                (page!!.width * scaleFactor).toInt(),
                (page.height * scaleFactor).toInt(),
                Bitmap.Config.ARGB_8888
            )

            // Fill the bitmap with a white background to avoid transparency issues
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfImageView.setImageBitmap(bitmap)
        }

        // Update UI
        val pageCount = pdfRenderer?.pageCount ?: 0
        pageNumberTextView.text = "Page ${index + 1} / $pageCount"
        prevButton.isEnabled = index > 0
        nextButton.isEnabled = index < pageCount - 1
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            currentPage?.close()
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
