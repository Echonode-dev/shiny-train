package com.cleandroid.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.cleandroid.app.databinding.ActivityMainBinding
import com.cleandroid.app.ui.bloat.BloatFragment
import com.cleandroid.app.ui.clean.CleanFragment
import com.cleandroid.app.ui.home.HomeFragment
import com.cleandroid.app.ui.scan.ScanFragment
import com.cleandroid.app.ui.shred.ShredFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Fragment instances are kept for the lifetime of the activity so switching
    // tabs is cheap on low-end devices (no re-inflation).
    private val homeFragment by lazy { HomeFragment() }
    private val bloatFragment by lazy { BloatFragment() }
    private val scanFragment by lazy { ScanFragment() }
    private val cleanFragment by lazy { CleanFragment() }
    private val shredFragment by lazy { ShredFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge (enforced on Android 15+): pad manually with system bar insets.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> select(homeFragment)
                R.id.nav_bloat -> select(bloatFragment)
                R.id.nav_scan -> select(scanFragment)
                R.id.nav_clean -> select(cleanFragment)
                R.id.nav_shred -> select(shredFragment)
                else -> false
            }
        }

        if (savedInstanceState == null) {
            select(homeFragment)
        } else {
            val id = savedInstanceState.getInt(KEY_TAB, R.id.nav_home)
            binding.bottomNav.selectedItemId = id
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
    }

    private fun select(fragment: Fragment): Boolean {
        if (supportFragmentManager.findFragmentById(R.id.container) === fragment) return true
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
        return true
    }

    companion object {
        private const val KEY_TAB = "selected_tab"
    }
}
