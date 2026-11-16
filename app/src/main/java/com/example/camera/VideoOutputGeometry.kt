package com.example.camera

/** Compare encoded geometry without confusing rotation metadata with resolution. */
internal object VideoOutputGeometry {
    fun matches(requestWidth: Int, requestHeight: Int, actualWidth: Int, actualHeight: Int): Boolean =
        requestWidth > 0 && requestHeight > 0 &&
            ((requestWidth == actualWidth && requestHeight == actualHeight) ||
                (requestWidth == actualHeight && requestHeight == actualWidth))
}
