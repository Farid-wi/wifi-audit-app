package com.wifiaudit.app.domain.usecase

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wifiaudit.app.domain.model.CanvasRoom
import com.wifiaudit.app.domain.model.RoomBounds
import com.wifiaudit.app.domain.model.RoomType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DetectRoomsUseCase @Inject constructor() {

    suspend operator fun invoke(bitmap: Bitmap): List<CanvasRoom> =
        suspendCancellableCoroutine { cont ->
            val image      = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val rooms = result.textBlocks
                        .filter { block -> block.text.trim().length in 2..25 }
                        .mapNotNull { block ->
                            val b = block.boundingBox ?: return@mapNotNull null
                            val label = block.text.trim().replaceFirstChar { it.uppercase() }
                            val type  = inferRoomType(label)
                            val halfW = (b.width().toFloat()  / bitmap.width)  * 0.75f
                            val halfH = (b.height().toFloat() / bitmap.height) * 1.0f
                            val cx    = b.centerX().toFloat() / bitmap.width
                            val cy    = b.centerY().toFloat() / bitmap.height
                            CanvasRoom(
                                id     = UUID.randomUUID().toString(),
                                type   = type,
                                label  = label,
                                bounds = RoomBounds(
                                    left   = (cx - halfW).coerceIn(0f, 1f),
                                    top    = (cy - halfH).coerceIn(0f, 1f),
                                    right  = (cx + halfW).coerceIn(0f, 1f),
                                    bottom = (cy + halfH).coerceIn(0f, 1f)
                                )
                            )
                        }
                    cont.resume(rooms)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun inferRoomType(label: String): RoomType {
        val l = label.lowercase()
        return when {
            l.contains("salon") || l.contains("séjour") || l.contains("living") -> RoomType.SALON
            l.contains("cuisine") || l.contains("kitchen")                       -> RoomType.KITCHEN
            l.contains("chambre") || l.contains("bedroom")                       -> RoomType.BEDROOM
            l.contains("bureau") || l.contains("office")                         -> RoomType.OFFICE
            l.contains("bain") || l.contains("wc") || l.contains("toilette")     -> RoomType.BATHROOM
            l.contains("couloir") || l.contains("hall") || l.contains("entrée")  -> RoomType.HALLWAY
            l.contains("manger") || l.contains("dining")                         -> RoomType.DINING
            else                                                                  -> RoomType.OTHER
        }
    }
}
