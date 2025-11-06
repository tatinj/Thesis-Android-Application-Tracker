package com.example.dashboard_and_security_module

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageButton // ✨ Changed from Button
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.appbar.MaterialToolbar // ✨ Added Toolbar import
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfViewerActivity : AppCompatActivity() {

    // --- Views ---
    private lateinit var pdfImageView: PhotoView
    private lateinit var pageNumberTextView: TextView
    private lateinit var prevButton: ImageButton // ✨ Updated type to ImageButton
    private lateinit var nextButton: ImageButton // ✨ Updated type to ImageButton
    private lateinit var toolbar: MaterialToolbar // ✨ Added Toolbar
    private lateinit var navigationControls: RelativeLayout // ✨ Added for hiding/showing

    // --- PDF Rendering ---
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        // --- Initialize Views ---
        toolbar = findViewById(R.id.toolbar)
        pdfImageView = findViewById(R.id.pdf_image_view)
        pageNumberTextView = findViewById(R.id.tv_page_number)
        prevButton = findViewById(R.id.btn_previous_page)
        nextButton = findViewById(R.id.btn_next_page)
        navigationControls = findViewById(R.id.navigation_controls)

        // --- Setup Toolbar ---
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            // This handles the back button press
            onBackPressedDispatcher.onBackPressed()
        }

        // --- PDF Loading ---
        val fileName = intent.getStringExtra("PDF_FILE_NAME")
        if (fileName != null) {
            try {
                openPdfRenderer(fileName)
                showPage(currentPageIndex)
            } catch (e: IOException) {
                Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                finish() // Close activity if PDF can't be opened
            }
        } else {
            Toast.makeText(this, "No PDF file specified.", Toast.LENGTH_LONG).show()
            finish()
        }

        // --- Event Listeners ---
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // ✨ Logic to hide/show UI for an immersive experience
        pdfImageView.setOnClickListener {
            toggleUiVisibility()
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

    // ✨ New function to show/hide the toolbar and navigation
    private fun toggleUiVisibility() {
        val isVisible = toolbar.visibility == View.VISIBLE
        if (isVisible) {
            toolbar.visibility = View.GONE
            navigationControls.visibility = View.GONE
        } else {
            toolbar.visibility = View.VISIBLE
            navigationControls.visibility = View.VISIBLE
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
        // Set the toolbar title with the document name
        supportActionBar?.title = fileName.removeSuffix(".pdf")
    }

    private fun showPage(index: Int) {
        currentPage?.close()
        currentPage = pdfRenderer?.openPage(index)?.also { page ->
            currentPageIndex = index

            // Render at a higher resolution for better quality when zooming.
            val scaleFactor = 2f
            val bitmap = Bitmap.createBitmap(
                (page.width * scaleFactor).toInt(),
                (page.height * scaleFactor).toInt(),
                Bitmap.Config.ARGB_8888
            )

            // Fill with a white background before rendering the page content
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
        // Clean up resources
        try {
            currentPage?.close()
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
