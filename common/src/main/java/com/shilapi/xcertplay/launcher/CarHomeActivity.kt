package com.shilapi.xcertplay.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.host.R

/**
 * DiPlay's optional car home screen: the car brand and photo chosen in setup, a clock, a large CarPlay
 * button and the driver's favourite apps. Disabled until the driver turns it on in the setup guide.
 */
class CarHomeActivity : Activity() {
    private lateinit var root: FrameLayout
    private var drawerOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = BG_TOP
        root = FrameLayout(this)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    @Deprecated("Home screens ignore Back; it only closes the app drawer.")
    override fun onBackPressed() {
        if (drawerOpen) { drawerOpen = false; render() }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // The Home button while already home closes the drawer.
        if (drawerOpen) { drawerOpen = false; render() }
    }

    private fun render() {
        root.removeAllViews()
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM))
        val apps = CarHome.launchableApps(this)
        if (!CarHome.hasSavedFavorites(this)) CarHome.saveFavorites(this, CarHome.defaultFavorites(apps, packageName))
        val landscape = resources.configuration.screenWidthDp >= resources.configuration.screenHeightDp
        val body = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        body.addView(brandPanel(landscape),
            if (landscape) LinearLayout.LayoutParams(0, -1, 0.38f) else LinearLayout.LayoutParams(-1, -2))
        body.addView(View(this), LinearLayout.LayoutParams(dp(20), dp(20)))
        body.addView(favoritesPanel(apps),
            if (landscape) LinearLayout.LayoutParams(0, -1, 0.62f) else LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        if (drawerOpen) root.addView(drawer(apps), FrameLayout.LayoutParams(-1, -1))
    }

    private fun brandPanel(landscape: Boolean): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(CARD, dp(24).toFloat())
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val photo = AirPlayPersistence.loadCustomAirPlayIconFile(this)?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?: BitmapFactory.decodeResource(resources, R.raw.ic_car_home)
        panel.addView(ImageView(this).apply {
            setImageBitmap(photo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.BLACK, dp(20).toFloat())
            clipToOutline = true
            contentDescription = AirPlayPersistence.loadOemLabel(this@CarHomeActivity)
        }, LinearLayout.LayoutParams(dp(if (landscape) 120 else 96), dp(if (landscape) 120 else 96)))
        panel.addView(text(AirPlayPersistence.loadOemLabel(this), 26, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
        })
        panel.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "h:mm"
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, if (landscape) 64f else 48f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        panel.addView(TextClock(this).apply {
            format24Hour = "EEEE d MMMM"; format12Hour = "EEEE d MMMM"
            setTextColor(MUTED); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        })
        panel.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        panel.addView(bigButton(getString(R.string.home_carplay), ACCENT) { openCarPlay() },
            LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(16) })
        panel.addView(bigButton(getString(R.string.home_diplay_settings), BUTTON) {
            startSafely(Intent(this, DiPlayActivity::class.java).putExtra("page", "settings"))
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) })
        return panel
    }

    private fun favoritesPanel(apps: List<ResolveInfo>): View {
        val byKey = apps.associateBy(CarHome::key)
        val favorites = CarHome.favorites(this).mapNotNull { byKey[it] }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, dp(24).toFloat())
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        panel.addView(text(getString(R.string.home_title_apps), 20, Color.WHITE, bold = true))
        panel.addView(text(getString(R.string.home_hint), 14, MUTED).apply { setPadding(0, dp(4), 0, dp(12)) })
        val tiles = favorites.map { info -> appTile(info, favorite = true) } +
            allAppsTile()
        panel.addView(ScrollView(this).apply { addView(grid(tiles, columns())) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return panel
    }

    private fun drawer(apps: List<ResolveInfo>): View {
        val favorites = CarHome.favorites(this).toSet()
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(0xF2, 0x0B, 0x10, 0x1A))
            setPadding(dp(24), dp(20), dp(24), dp(20))
            isClickable = true
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(getString(R.string.home_all_apps), 24, Color.WHITE, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(bigButton("✕", BUTTON) { drawerOpen = false; render() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        overlay.addView(header)
        overlay.addView(text(getString(R.string.home_drawer_hint), 14, MUTED).apply { setPadding(0, dp(4), 0, dp(12)) })
        val tiles = apps.filter { it.activityInfo.packageName != packageName || it.activityInfo.name == DiPlayActivity::class.java.name }
            .map { info -> appTile(info, favorite = false, starred = CarHome.key(info) in favorites) }
        overlay.addView(ScrollView(this).apply { addView(grid(tiles, columns() + 1)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return overlay
    }

    private fun appTile(info: ResolveInfo, favorite: Boolean, starred: Boolean = false): View {
        val key = CarHome.key(info)
        val label = info.loadLabel(packageManager).toString()
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(if (starred) STARRED else TILE, dp(18).toFloat())
            setPadding(dp(8), dp(12), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        tile.addView(ImageView(this).apply {
            setImageDrawable(runCatching { info.loadIcon(packageManager) }.getOrNull())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        tile.addView(text(if (starred) "★ $label" else label, 14, Color.WHITE).apply {
            gravity = Gravity.CENTER; maxLines = 2; setPadding(0, dp(6), 0, 0)
        })
        tile.setOnClickListener {
            startSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        tile.setOnLongClickListener {
            if (favorite) favoriteMenu(key, label) else {
                val added = CarHome.toggleFavorite(this, key)
                if (!added && key !in CarHome.favorites(this) && !starred) {
                    Toast.makeText(this, R.string.home_favorites_full, Toast.LENGTH_SHORT).show()
                }
                render()
            }
            true
        }
        return tile
    }

    private fun favoriteMenu(key: String, label: String) {
        val options = arrayOf(
            getString(R.string.home_move_left),
            getString(R.string.home_move_right),
            getString(R.string.home_remove_favorite),
        )
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> CarHome.moveFavorite(this, key, -1)
                    1 -> CarHome.moveFavorite(this, key, 1)
                    else -> CarHome.toggleFavorite(this, key)
                }
                render()
            }
            .show()
    }

    private fun allAppsTile(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(BUTTON, dp(18).toFloat())
        setPadding(dp(8), dp(12), dp(8), dp(10))
        isClickable = true
        contentDescription = getString(R.string.home_all_apps)
        addView(text("⋯", 36, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(text(getString(R.string.home_all_apps), 14, Color.WHITE).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        setOnClickListener { drawerOpen = true; render() }
    }

    private fun grid(tiles: List<View>, columns: Int): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tiles.chunked(columns).forEachIndexed { rowIndex, line ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.forEachIndexed { index, tile ->
                row.addView(tile, LinearLayout.LayoutParams(0, dp(118), 1f).apply { if (index > 0) marginStart = dp(12) })
            }
            repeat(columns - line.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(118), 1f).apply { marginStart = dp(12) })
            }
            column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(12) })
        }
        return column
    }

    private fun columns(): Int {
        val width = resources.configuration.screenWidthDp
        val landscape = width >= resources.configuration.screenHeightDp
        val panel = if (landscape) width * 0.62f else width.toFloat()
        return (panel / 130f).toInt().coerceIn(3, 6)
    }

    private fun openCarPlay() {
        // Same as DiPlay's own icon: resumes a running CarPlay session, otherwise opens DiPlay's home page.
        startSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClass(this, DiPlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startSafely(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.home_cannot_open, Toast.LENGTH_SHORT).show() }
    }

    private fun bigButton(title: String, color: Int, click: () -> Unit) = TextView(this).apply {
        text = title
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = rounded(color, dp(18).toFloat())
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = ViewGroup.LayoutParams(-1, -2)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        val BG_TOP = Color.rgb(0x1A, 0x22, 0x33)
        val BG_BOTTOM = Color.rgb(0x07, 0x0A, 0x12)
        val CARD = Color.argb(0x66, 0x22, 0x2C, 0x40)
        val TILE = Color.argb(0x80, 0x2A, 0x36, 0x4C)
        val STARRED = Color.argb(0xB0, 0x2E, 0x5A, 0x8F)
        val BUTTON = Color.rgb(0x2E, 0x3A, 0x52)
        val ACCENT = Color.rgb(0x2F, 0x7B, 0xF6)
        val MUTED = Color.rgb(0xA8, 0xB3, 0xC7)
    }
}
