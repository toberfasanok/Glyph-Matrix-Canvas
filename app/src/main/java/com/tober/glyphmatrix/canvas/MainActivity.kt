package com.tober.glyphmatrix.canvas

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.graphics.set
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size

import com.tober.glyphmatrix.canvas.ui.theme.GlyphMatrixCanvasTheme

import java.io.OutputStream

import kotlin.math.min

class MainActivity : ComponentActivity() {
    private val tag = "Main Activity"

    private val matrixSize = 25

    private val mask: List<Boolean> = run {
        val raw = listOf(
            "0000000001111111000000000",
            "0000000111111111110000000",
            "0000011111111111111100000",
            "0000111111111111111110000",
            "0001111111111111111111000",
            "0011111111111111111111100",
            "0011111111111111111111100",
            "0111111111111111111111110",
            "0111111111111111111111110",
            "1111111111111111111111111",
            "1111111111111111111111111",
            "1111111111111111111111111",
            "1111111111111111111111111",
            "1111111111111111111111111",
            "1111111111111111111111111",
            "1111111111111111111111111",
            "0111111111111111111111110",
            "0111111111111111111111110",
            "0011111111111111111111100",
            "0011111111111111111111100",
            "0001111111111111111111000",
            "0000111111111111111110000",
            "0000011111111111111100000",
            "0000000111111111110000000",
            "0000000001111111000000000"
        )

        val arr = BooleanArray(matrixSize * matrixSize) { false }

        for (r in 0 until matrixSize) {
            val row = raw[r]
            for (c in 0 until matrixSize) {
                arr[r * matrixSize + c] = (row[c] == '1')
            }
        }

        arr.toList()
    }

    private val cells: SnapshotStateList<Boolean> = run {
        val list = mutableStateListOf<Boolean>()
        repeat(matrixSize * matrixSize) { list.add(false) }
        list
    }

    private lateinit var saveImageLauncher: ActivityResultLauncher<String>
    private lateinit var loadImageLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        saveImageLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }

            try {
                val bitmap = createBitmap(matrixSize, matrixSize)

                val on = 0xFFFFFFFF.toInt()
                val off = 0x00000000

                for (r in 0 until matrixSize) {
                    for (c in 0 until matrixSize) {
                        val i = r * matrixSize + c
                        val pixel = when {
                            !mask[i] -> off
                            cells[i] -> on
                            else -> off
                        }
                        bitmap[c, r] = pixel
                    }
                }

                contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }

                toast("Saved")
            } catch (e: Exception) {
                Log.e(tag, "Failed to save glyph: $e")
                toast("Failed to save glyph")
            }
        }

        loadImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }

            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val original = BitmapFactory.decodeStream(input)
                        ?: throw Exception("Failed to decode image")

                    val scaled = original.scale(matrixSize, matrixSize)

                    for (r in 0 until matrixSize) {
                        for (c in 0 until matrixSize) {
                            val i = r * matrixSize + c
                            if (!mask[i]) {
                                cells[i] = false
                                continue
                            }

                            val px = scaled[c, r]
                            val red = (px shr 16) and 0xFF
                            val green = (px shr 8) and 0xFF
                            val blue = px and 0xFF
                            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
                            cells[i] = luminance >= 128.0
                        }
                    }

                    toast("Loaded")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load image: $e")
                toast("Failed to load image")
            }
        }

        setContent {
            GlyphMatrixCanvasTheme {
                val config = LocalConfiguration.current
                val density = LocalDensity.current

                val cellSpacing = 2.dp

                val screenMin = min(config.screenWidthDp, config.screenHeightDp)
                val gridSize = (screenMin * 0.95f).dp

                val cellSize = (gridSize - cellSpacing * (matrixSize - 1)) / matrixSize

                val cellSizePx = with(density) { cellSize.toPx() }
                val cellSpacingPx = with(density) { cellSpacing.toPx() }

                var paintMode by remember { mutableStateOf<Boolean?>(null) }

                val onColor = MaterialTheme.colorScheme.primary
                val offColor = MaterialTheme.colorScheme.surfaceVariant

                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { delete() }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete")
                            }

                            IconButton(onClick = { display() }) {
                                Icon(imageVector = Icons.Filled.Visibility, contentDescription = "Display")
                            }
                        }

                        Box(
                            modifier = Modifier.size(gridSize)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val (r, c) = getCellPosition(offset.x, offset.y, cellSizePx, cellSpacingPx)

                                        if (r in 0 until matrixSize && c in 0 until matrixSize) {
                                            val i = r * matrixSize + c

                                            if (mask[i]) {
                                                cells[i] = !cells[i]
                                            }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset: Offset ->
                                            val (r, c) = getCellPosition(offset.x, offset.y, cellSizePx, cellSpacingPx)

                                            if (r in 0 until matrixSize && c in 0 until matrixSize) {
                                                val i = r * matrixSize + c

                                                if (!mask[i]) {
                                                    paintMode = null
                                                    return@detectDragGestures
                                                }

                                                val new = !cells[i]
                                                cells[i] = new
                                                paintMode = new
                                            }
                                        },

                                        onDrag = { change, _ ->
                                            change.consume()
                                            val position = change.position
                                            val (r, c) = getCellPosition(position.x, position.y, cellSizePx, cellSpacingPx)

                                            if (r in 0 until matrixSize && c in 0 until matrixSize) {
                                                val i = r * matrixSize + c

                                                if (!mask[i]) return@detectDragGestures

                                                paintMode?.let { desired ->
                                                    if (cells[i] != desired) {
                                                        cells[i] = desired
                                                    }
                                                }
                                            }
                                        },

                                        onDragEnd = { paintMode = null },
                                        onDragCancel = { paintMode = null }
                                    )
                                }
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val corner = (cellSizePx * 0.18f)
                                for (r in 0 until matrixSize) {
                                    for (c in 0 until matrixSize) {
                                        val i = r * matrixSize + c
                                        if (!mask[i]) continue

                                        val left = c * (cellSizePx + cellSpacingPx)
                                        val top = r * (cellSizePx + cellSpacingPx)
                                        val color = if (cells[i]) onColor else offColor
                                        drawRoundRect(
                                            color = color,
                                            topLeft = Offset(left, top),
                                            size = Size(cellSizePx, cellSizePx),
                                            cornerRadius = CornerRadius(corner, corner)
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { save() }) {
                                    Icon(imageVector = Icons.Filled.Save, contentDescription = "Save")
                                }

                                IconButton(onClick = { load() }) {
                                    Icon(imageVector = Icons.Filled.FolderOpen, contentDescription = "Load")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { moveCellPositions(-1, 0) }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left")
                                }

                                IconButton(onClick = { moveCellPositions(1, 0) }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right")
                                }

                                IconButton(onClick = { moveCellPositions(0, -1) }) {
                                    Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = "Up")
                                }

                                IconButton(onClick = { moveCellPositions(0, 1) }) {
                                    Icon(imageVector = Icons.Filled.ArrowDownward, contentDescription = "Down")
                                }

                                IconButton(onClick = { reverse() }) {
                                    Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = "Reverse")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getCellPosition(x: Float, y: Float, cellSizePx: Float, cellSpacingPx: Float): Pair<Int, Int> {
        if (x < 0f || y < 0f) return Pair(-1, -1)

        val matrixX = (x / (cellSizePx + cellSpacingPx)).toInt()
        val matrixY = (y / (cellSizePx + cellSpacingPx)).toInt()

        if (matrixX < 0 || matrixY < 0 || matrixX >= matrixSize || matrixY >= matrixSize) return Pair(-1, -1)

        val localX = x - matrixX * (cellSizePx + cellSpacingPx)
        val localY = y - matrixY * (cellSizePx + cellSpacingPx)

        if (localX > cellSizePx || localY > cellSizePx) {
            return Pair(-1, -1)
        }

        return Pair(matrixY, matrixX)
    }

    private fun moveCellPositions(dx: Int, dy: Int) {
        val arr = BooleanArray(matrixSize * matrixSize) { false }

        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                val i = r * matrixSize + c
                if (!mask[i]) continue
                if (!cells[i]) continue

                val newR = r + dy
                val newC = c + dx

                if (newR in 0 until matrixSize && newC in 0 until matrixSize) {
                    val newI = newR * matrixSize + newC
                    if (mask[newI]) {
                        arr[newI] = true
                    }
                }
            }
        }

        for (i in 0 until matrixSize * matrixSize) {
            if (mask[i]) {
                cells[i] = arr[i]
            } else {
                cells[i] = false
            }
        }
    }

    private fun delete() {
        for (i in 0 until matrixSize * matrixSize) {
            if (mask[i]) cells[i] = false
        }
    }

    private fun reverse() {
        for (i in 0 until matrixSize * matrixSize) {
            if (mask[i]) cells[i] = !cells[i]
        }
    }

    private fun save() {
        saveImageLauncher.launch("glyph_${System.currentTimeMillis()}.png")
    }

    private fun load() {
        loadImageLauncher.launch(arrayOf("image/*"))
    }

    private fun display() {
        try {
            val bitmap = createBitmap(matrixSize, matrixSize, Bitmap.Config.ARGB_8888)
            val on = 0xFFFFFFFF.toInt()
            val off = 0x00000000

            for (r in 0 until matrixSize) {
                for (c in 0 until matrixSize) {
                    val i = r * matrixSize + c

                    val px = when {
                        !mask[i] -> off
                        cells[i] -> on
                        else -> off
                    }

                    bitmap[c, r] = px
                }
            }

            val intent = Intent(this@MainActivity, GlyphMatrixService::class.java).apply {
                putExtra(Constants.GLYPH_EXTRA_BITMAP, bitmap)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to display glyph: $e")
            toast("Failed to display glyph")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
    }
}
