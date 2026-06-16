package com.hiraeth.flame

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animate icon + title in
        val icon = findViewById<View>(R.id.splashIcon)
        val title = findViewById<View>(R.id.splashTitle)
        val divider = findViewById<View>(R.id.splash_divider)
        val quoteContainer = findViewById<View>(R.id.quote_container)
        val quote = findViewById<View>(R.id.splashQuote)
        val author = findViewById<View>(R.id.splashAuthor)

        // Start invisible
        listOf(icon, title, divider, quoteContainer, quote, author).forEach {
            it?.alpha = 0f
            it?.translationY = 40f
        }

        val delay = 120L
        fun animateIn(v: View?, d: Long) {
            v ?: return
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 40f, 0f),
                )
                duration = 500
                startDelay = d
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        animateIn(icon, 0)
        animateIn(title, delay)
        animateIn(divider, delay * 2)
        animateIn(quoteContainer, delay * 3)
        animateIn(quote, (delay * 3 + 100))
        animateIn(author, delay * 4)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2600)
    }
}
