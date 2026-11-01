package com.shilapi.xcertplay.setup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.shilapi.xcertplay.AirPlayPersistence
import java.io.ByteArrayOutputStream

/**
 * The car brand the driver picks in the setup guide. It names CarPlay's "back to the car" button and,
 * until the driver chooses a photo, draws a neutral monogram for it.
 *
 * The monogram is deliberately generic (same shape and colours for every brand, initials only): it
 * never reproduces a manufacturer's logo. Drivers who want their own badge or a photo of their car
 * pick an image, which always wins over the monogram.
 */
enum class CarBrand(val id: String, val label: String, val initials: String) {
    FORD("ford", "Ford", "FO"),
    CHEVROLET("chevrolet", "Chevrolet", "CH"),
    TOYOTA("toyota", "Toyota", "TO"),
    MAZDA("mazda", "Mazda", "MZ"),
    NISSAN("nissan", "Nissan", "NI"),
    RENAULT("renault", "Renault", "RE"),
    KIA("kia", "Kia", "KI"),
    HYUNDAI("hyundai", "Hyundai", "HY"),
    VOLKSWAGEN("volkswagen", "Volkswagen", "VW"),
    HONDA("honda", "Honda", "HO"),
    MITSUBISHI("mitsubishi", "Mitsubishi", "MI"),
    SUZUKI("suzuki", "Suzuki", "SU"),
    JEEP("jeep", "Jeep", "JE"),
    PEUGEOT("peugeot", "Peugeot", "PE"),
    BYD("byd", "BYD", "BY"),
    OTHER("other", "", "");

    companion object {
        private const val PREFS = "diplay"
        private const val KEY_BRAND = "car_brand"
        /** True once the driver picked a photo; a later brand change then keeps that photo. */
        private const val KEY_ICON_FROM_PHOTO = "car_icon_from_photo"
        private const val ICON_PIXELS = 1024

        fun fromId(id: String?): CarBrand? = entries.firstOrNull { it.id == id }

        fun saved(context: Context): CarBrand? =
            fromId(prefs(context).getString(KEY_BRAND, null))

        fun iconFromPhoto(context: Context): Boolean = prefs(context).getBoolean(KEY_ICON_FROM_PHOTO, false)

        fun markIconFromPhoto(context: Context, fromPhoto: Boolean) {
            prefs(context).edit().putBoolean(KEY_ICON_FROM_PHOTO, fromPhoto).apply()
        }

        /**
         * Saves [brand], names the car button after it and, unless the driver already chose a photo,
         * replaces the button icon with the brand monogram. Returns true when the CarPlay button changed.
         */
        fun choose(context: Context, brand: CarBrand, customName: String? = null): Boolean {
            prefs(context).edit().putString(KEY_BRAND, brand.id).apply()
            val name = customName?.trim().orEmpty().ifBlank { brand.label }
            if (name.isNotBlank()) AirPlayPersistence.saveOemLabel(context, name)
            if (!iconFromPhoto(context)) {
                if (brand.initials.isEmpty()) {
                    AirPlayPersistence.clearCustomAirPlayIcon(context)
                } else {
                    AirPlayPersistence.saveCustomAirPlayIcon(context, monogramPng(brand.initials))
                }
            }
            return true
        }

        /** Drops the photo and goes back to the monogram (or the default car icon for "Other"). */
        fun useMonogram(context: Context) {
            markIconFromPhoto(context, false)
            val brand = saved(context)
            if (brand == null || brand.initials.isEmpty()) {
                AirPlayPersistence.clearCustomAirPlayIcon(context)
            } else {
                AirPlayPersistence.saveCustomAirPlayIcon(context, monogramPng(brand.initials))
            }
        }

        fun monogram(initials: String, size: Int = ICON_PIXELS): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val s = size.toFloat()
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, s, Color.rgb(0x2A, 0x36, 0x4A), Color.rgb(0x12, 0x18, 0x24), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, s, s, background)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.035f
                color = Color.rgb(0x7F, 0xB7, 0xFF)
            }
            val inset = s * 0.14f
            canvas.drawOval(RectF(inset, inset, s - inset, s - inset), ring)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = s * (if (initials.length > 1) 0.30f else 0.40f)
                letterSpacing = 0.04f
            }
            val baseline = s / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(initials.uppercase(), s / 2f, baseline, text)
            return bitmap
        }

        fun monogramPng(initials: String): ByteArray {
            val bitmap = monogram(initials)
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
