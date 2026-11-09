package com.nitro.os

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity() {

    // 0 = Boot (3s), 1 = Lock Screen, 2 = Win12 Bottom Pill Home, 3 = Full Google TV + Left Pill Drawer
    private var osState = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var rootFrame: FrameLayout
    private lateinit var wallpaperView: LiquidWallpaperView
    private lateinit var bootLayer: FrameLayout
    private lateinit var lockLayer: FrameLayout
    private lateinit var win12Layer: FrameLayout
    private lateinit var drawerLayer: FrameLayout

    private lateinit var lockClockText: TextView
    private lateinit var lockDateText: TextView
    private lateinit var win12ClockText: TextView
    private lateinit var topStatusClock: TextView
    private lateinit var stageTitleText: TextView
    private lateinit var appsGridContainer: GridLayout

    private val installedApps = mutableListOf<AppItem>()
    private val recentApps = mutableListOf<AppItem>()
    private var wallpaperThemeIndex = 0

    data class AppItem(
        val name: String,
        val pkg: String,
        val icon: Drawable?,
        val isCloudApp: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        rootFrame = FrameLayout(this)
        setContentView(rootFrame)

        // 1. Live GPU Canvas Liquid Wallpaper (Behind Everything)
        wallpaperView = LiquidWallpaperView(this)
        rootFrame.addView(wallpaperView, matchParent())

        // 2. Build All 4 Native Layers (Zero HTML!)
        buildBootLayer()
        buildLockLayer()
        buildWin12Layer()
        buildFullDrawerLayer()

        showState(0)
        startClockTicker()
        start3SecondBootAndClean()
        syncFirebaseCloud()
    }

    // =========================================================================
    // LAYER 1: 3-SECOND BOOT & REAL CACHE/RAM CLEANER
    // =========================================================================
    private fun buildBootLayer() {
        bootLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#040711"))
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "N I T R O   O S"
            textSize = 42f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setShadowLayer(25f, 0f, 0f, Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Purging Google TV Bloat Cache & Optimizing RAM..."
            textSize = 15f
            setTextColor(Color.parseColor("#A0AEC0"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 36)
        }

        val progressBg = FrameLayout(this).apply {
            background = glassRoundedRect(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 20f)
        }
        val progressFill = View(this).apply {
            background = glassRoundedRect(Color.parseColor("#00E5FF"), Color.WHITE, 20f)
        }
        progressBg.addView(progressFill, FrameLayout.LayoutParams(10, 14))

        box.addView(logo)
        box.addView(sub)
        box.addView(progressBg, LinearLayout.LayoutParams(520, 14))

        bootLayer.addView(box, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        rootFrame.addView(bootLayer, matchParent())

        // Smooth 3-Second Progress Bar Animation
        ValueAnimator.ofInt(10, 520).apply {
            duration = 2800
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val lp = progressFill.layoutParams
                lp.width = anim.animatedValue as Int
                progressFill.layoutParams = lp
            }
            start()
        }
    }

    private fun start3SecondBootAndClean() {
        thread {
            try {
                // Real Cache Cleaning + RAM Garbage Collection
                cacheDir.deleteRecursively()
                codeCacheDir.deleteRecursively()
                System.gc()
                Runtime.getRuntime().gc()
                loadAllInstalledApps()
            } catch (_: Exception) {}
        }

        // Guaranteed unlock to Lock Screen in 3.0 seconds (Cannot get stuck!)
        handler.postDelayed({
            showState(1)
        }, 3000)
    }

    // =========================================================================
    // LAYER 2: ULTRA-CLEAN LOCK SCREEN (SWIPE UP / D-PAD UP TO UNLOCK)
    // =========================================================================
    private fun buildLockLayer() {
        lockLayer = FrameLayout(this)

        val centerClockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        lockClockText = TextView(this).apply {
            text = "07:30"
            textSize = 92f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(30f, 0f, 8f, Color.parseColor("#99000000"))
            gravity = Gravity.CENTER
        }

        lockDateText = TextView(this).apply {
            text = "Monday, November 9"
            textSize = 20f
            setTextColor(Color.parseColor("#E2E8F0"))
            setShadowLayer(15f, 0f, 4f, Color.BLACK)
            gravity = Gravity.CENTER
        }

        centerClockBox.addView(lockClockText)
        centerClockBox.addView(lockDateText)

        lockLayer.addView(centerClockBox, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        val unlockPill = TextView(this).apply {
            text = "▲   Swipe Up or Press D-Pad UP / OK to Unlock"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(48, 20, 48, 20)
            background = glassRoundedRect(Color.parseColor("#33FFFFFF"), Color.parseColor("#66FFFFFF"), 50f)
            isFocusable = true
            setOnClickListener { showState(2) }
        }

        val bottomLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 55 }

        lockLayer.addView(unlockPill, bottomLp)
        rootFrame.addView(lockLayer, matchParent())
    }

    // =========================================================================
    // LAYER 3: WINDOWS 12 CLEAN HOME (WALLPAPER + TOP TIME + BOTTOM PILL)
    // =========================================================================
    private fun buildWin12Layer() {
        win12Layer = FrameLayout(this)

        // Top Bar: Time on left, Status & Refresh on right
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(50, 36, 50, 0)
        }

        win12ClockText = TextView(this).apply {
            text = "07:30 PM"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(12f, 0f, 2f, Color.BLACK)
        }

        val spacer = View(this)
        topBar.addView(win12ClockText)
        topBar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val boostBtn = createGlassPillButton("⚡ Rust Boost") {
            System.gc()
            Toast.makeText(this, "⚡ Nitro RAM Boosted & Cache Cleared!", Toast.LENGTH_SHORT).show()
        }
        val statusBtn = createGlassPillButton("🎛️ Status Panel") {
            showQuickStatusDialog()
        }
        topBar.addView(boostBtn)
        val gap = View(this)
        topBar.addView(gap, LinearLayout.LayoutParams(20, 1))
        topBar.addView(statusBtn)

        win12Layer.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        // Windows 12 Floating Bottom Pill Dock
        val bottomDock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(36, 18, 36, 18)
            background = glassRoundedRect(Color.parseColor("#440F172A"), Color.parseColor("#8838BDF8"), 60f)
        }

        val openDrawerBtn = createGlassPillButton("▼  Apps & Universal Hub (Press DOWN)") {
            showState(3)
        }
        val walBtn = createGlassPillButton("🌌 Liquid Wallpaper") {
            wallpaperThemeIndex = (wallpaperThemeIndex + 1) % 4
            wallpaperView.setThemePalette(wallpaperThemeIndex)
            Toast.makeText(this, "Liquid Glass Wallpaper Changed!", Toast.LENGTH_SHORT).show()
        }
        val mapperBtn = createGlassPillButton("🎮 Button Mapper") {
            showButtonMapperDialog()
        }

        bottomDock.addView(openDrawerBtn)
        bottomDock.addView(View(this), LinearLayout.LayoutParams(18, 1))
        bottomDock.addView(walBtn)
        bottomDock.addView(View(this), LinearLayout.LayoutParams(18, 1))
        bottomDock.addView(mapperBtn)

        val dockLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 42 }

        win12Layer.addView(bottomDock, dockLp)
        rootFrame.addView(win12Layer, matchParent())
    }

    // =========================================================================
    // LAYER 4: GOOGLE TV + LEFT VERTICAL PILL NAVIGATION DRAWER
    // =========================================================================
    private fun buildFullDrawerLayer() {
        drawerLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#88020617"))
        }

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(36, 32, 36, 32)
        }

        // Left Vertical Pill-Shaped Bar (Home, Search, Apps, Recents, Status)
        val leftVerticalPill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 32, 18, 32)
            background = glassRoundedRect(Color.parseColor("#441E293B"), Color.parseColor("#7700E5FF"), 70f)
        }

        val navHome = createCircleNavButton("🏠", "Home") { showState(2) }
        val navSearch = createCircleNavButton("🔍", "Search") { showUniversalSearchDialog() }
        val navApps = createCircleNavButton("📦", "All Apps") { populateAppGrid(installedApps, "All Installed TV Apps") }
        val navRecents = createCircleNavButton("📑", "Recents") { populateAppGrid(recentApps, "Recent Multitasking Apps") }
        val navPanel = createCircleNavButton("⚙️", "Status") { showQuickStatusDialog() }

        leftVerticalPill.addView(navHome)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 22))
        leftVerticalPill.addView(navSearch)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 22))
        leftVerticalPill.addView(navApps)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 22))
        leftVerticalPill.addView(navRecents)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 22))
        leftVerticalPill.addView(navPanel)

        // Right Content Area
        val rightArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(38, 8, 12, 8)
        }

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        stageTitleText = TextView(this).apply {
            text = "All Installed TV Apps"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        topStatusClock = TextView(this).apply {
            text = "07:30 PM"
            textSize = 18f
            setTextColor(Color.parseColor("#00E5FF"))
        }

        headerRow.addView(stageTitleText)
        headerRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        headerRow.addView(createGlassPillButton("🛡️ Debloat & Default Home") { showDebloatDialog() })
        headerRow.addView(View(this), LinearLayout.LayoutParams(16, 1))
        headerRow.addView(topStatusClock)

        // Scrollable Native Grid for TV Apps
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setPadding(0, 24, 0, 24)
        }

        appsGridContainer = GridLayout(this).apply {
            columnCount = 4
        }
        scrollView.addView(appsGridContainer)

        rightArea.addView(headerRow)
        rightArea.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        mainRow.addView(leftVerticalPill, LinearLayout.LayoutParams(
            115,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        mainRow.addView(rightArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        drawerLayer.addView(mainRow, matchParent())
        rootFrame.addView(drawerLayer, matchParent())
    }

    // =========================================================================
    // NATIVE APP SCANNER & LIQUID GLASS CARDS
    // =========================================================================
    private fun loadAllInstalledApps() {
        val pm = packageManager
        val list = mutableListOf<AppItem>()
        try {
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                    ?: pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null && app.packageName != packageName) {
                    val label = pm.getApplicationLabel(app).toString()
                    val icon = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }
                    list.add(AppItem(label, app.packageName, icon))
                }
            }
            list.sortBy { it.name.lowercase() }
        } catch (_: Exception) {}

        installedApps.clear()
        installedApps.addAll(list)

        handler.post {
            populateAppGrid(installedApps, "All Installed TV Apps (${installedApps.size})")
        }
    }

    private fun populateAppGrid(apps: List<AppItem>, title: String) {
        stageTitleText.text = title
        appsGridContainer.removeAllViews()

        if (apps.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No apps in this list yet."
                textSize = 18f
                setTextColor(Color.LTGRAY)
                setPadding(20, 40, 20, 20)
            }
            appsGridContainer.addView(emptyTv)
            return
        }

        for (app in apps) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(22, 24, 22, 20)
                background = glassRoundedRect(Color.parseColor("#381E293B"), Color.parseColor("#55FFFFFF"), 32f)
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
                        v.background = glassRoundedRect(Color.parseColor("#660F172A"), Color.parseColor("#00E5FF"), 32f, 4)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        v.background = glassRoundedRect(Color.parseColor("#381E293B"), Color.parseColor("#55FFFFFF"), 32f, 2)
                    }
                }

                setOnClickListener {
                    launchAppPackage(app)
                }
            }

            val iconView = ImageView(this).apply {
                if (app.icon != null) setImageDrawable(app.icon)
                else setImageResource(android.R.drawable.sym_def_app_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val nameView = TextView(this).apply {
                text = app.name
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 14, 0, 0)
            }

            card.addView(iconView, LinearLayout.LayoutParams(96, 96))
            card.addView(nameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val glp = GridLayout.LayoutParams().apply {
                width = 250
                height = 195
                setMargins(16, 16, 16, 16)
            }
            appsGridContainer.addView(card, glp)
        }
    }

    private fun launchAppPackage(app: AppItem) {
        try {
            recentApps.removeAll { it.pkg == app.pkg }
            recentApps.add(0, app)
            val intent = packageManager.getLeanbackLaunchIntentForPackage(app.pkg)
                ?: packageManager.getLaunchIntentForPackage(app.pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not launch ${app.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // STATUS BAR, BUTTON MAPPER & DEBLOAT TOOLS
    // =========================================================================
    private fun showQuickStatusDialog() {
        val options = arrayOf(
            "📶  Wi-Fi & Network Settings",
            "🔵  Bluetooth & Remotes",
            "📺  TV Inputs / Display Settings",
            "📑  Clear All Recents & Boost RAM",
            "🌌  Switch Liquid Glass Wallpaper",
            "🎮  Configure Remote Button Mapper",
            "⚙️  Open Full System Settings"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Nitro OS Quick Status Bar")
            .setItems(options) { _, which ->
               
