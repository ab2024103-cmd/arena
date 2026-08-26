package net.morsecode.shared.thumbnail

/** Small JPEG/PNG thumbnail for TRANSFER_REQUEST.thumbnail_base64 (Section 5). */
expect fun generateThumbnailBase64(uri: String, maxPx: Int = 256): String?
