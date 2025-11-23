package com.example.dashboard_and_security_module

import android.os.Bundle
import android.widget.ImageButton // <-- IMPORT the correct type
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SlideshowActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slideshow)

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val tabLayout: TabLayout = findViewById(R.id.tab_indicator)


        val closeButton: ImageButton = findViewById(R.id.btn_close)

        // IMPORTANT: Make sure you have these images in your res/drawable folder
        val tutorialImages = listOf(
            R.drawable.slide1,
            R.drawable.slide2,
            R.drawable.slide3,
            R.drawable.slide4,
            R.drawable.slide5,
            R.drawable.slide6,
            R.drawable.slide7,
            R.drawable.slide8,
            R.drawable.slide9,
            R.drawable.slide10,
            R.drawable.slide11,
            R.drawable.slide12,
            R.drawable.slide13,

            // Add as many as you need
        )

        val adapter = SlideshowAdapter(tutorialImages)
        viewPager.adapter = adapter

        // This one line connects the dots to the ViewPager, making them work automatically.
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // No code needed here, it works automatically!
        }.attach()

        closeButton.setOnClickListener {
            finish() // Close the slideshow
        }
    }
}
