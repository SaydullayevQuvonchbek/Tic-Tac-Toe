package com.example.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var logic: ChessLogic? = null
        set(value) {
            field = value
            invalidate()
        }

    var boardTheme: ChessThemeManager.BoardTheme = ChessThemeManager.BOARD_THEMES[0]
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var pieceSkin: ChessThemeManager.PieceSkin = ChessThemeManager.PIECE_SKINS[0]
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var isFlipped: Boolean = false // True if local player is Black (plays from bottom)
        set(value) {
            field = value
            invalidate()
        }

    var selectedSquare: Pair<Int, Int>? = null
        set(value) {
            field = value
            invalidate()
        }

    var validMoves: List<ChessMove> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var lastMove: ChessMove? = null
        set(value) {
            field = value
            invalidate()
        }

    var onSquareTapped: ((row: Int, col: Int) -> Unit)? = null

    // Paints
    private val lightSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lastMoveSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 160 }
    private val checkSquarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 180 }

    private val validDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        alpha = 90
        style = Paint.Style.FILL
    }

    private val captureRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        alpha = 110
        style = Paint.Style.STROKE
    }

    private val pieceWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val pieceWhiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val pieceBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val pieceBlackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        updatePaints()
    }

    private fun updatePaints() {
        lightSquarePaint.color = boardTheme.lightColor
        darkSquarePaint.color = boardTheme.darkColor
        selectedSquarePaint.color = boardTheme.selectedColor
        lastMoveSquarePaint.color = boardTheme.lastMoveColor
        checkSquarePaint.color = boardTheme.checkColor

        pieceWhitePaint.color = pieceSkin.whiteColor
        pieceWhiteStrokePaint.color = pieceSkin.whiteStrokeColor

        pieceBlackPaint.color = pieceSkin.blackColor
        if (pieceSkin.blackStrokeColor != null) {
            pieceBlackStrokePaint.color = pieceSkin.blackStrokeColor!!
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width < height && width > 0) width else if (height > 0) height else width
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cellSize = w / 8f
        val currentLogic = logic

        // 1. Draw Board Squares
        for (r in 0..7) {
            for (c in 0..7) {
                val displayR = if (isFlipped) 7 - r else r
                val displayC = if (isFlipped) 7 - c else c

                val left = displayC * cellSize
                val top = displayR * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                val isLight = (r + c) % 2 == 0
                val bgPaint = if (isLight) lightSquarePaint else darkSquarePaint
                canvas.drawRect(left, top, right, bottom, bgPaint)

                // Last Move Highlight
                val lm = lastMove
                if (lm != null && ((lm.fromR == r && lm.fromC == c) || (lm.toR == r && lm.toC == c))) {
                    canvas.drawRect(left, top, right, bottom, lastMoveSquarePaint)
                }

                // Selected Square Highlight
                if (selectedSquare?.first == r && selectedSquare?.second == c) {
                    canvas.drawRect(left, top, right, bottom, selectedSquarePaint)
                }

                // King Check Highlight
                if (currentLogic != null && currentLogic.isCheck) {
                    val checkedKing = currentLogic.findKing(currentLogic.currentTurn)
                    if (checkedKing != null && checkedKing.first == r && checkedKing.second == c) {
                        canvas.drawRect(left, top, right, bottom, checkSquarePaint)
                    }
                }

                // Coordinates (rank numbers on left edge, file letters on bottom edge)
                coordPaint.color = if (isLight) boardTheme.darkColor else boardTheme.lightColor
                if (displayC == 0) {
                    val rankText = (8 - r).toString()
                    canvas.drawText(rankText, left + 6f, top + 26f, coordPaint)
                }
                if (displayR == 7) {
                    val fileText = ('a' + c).toString()
                    canvas.drawText(fileText, right - 20f, bottom - 8f, coordPaint)
                }
            }
        }

        // 2. Draw Valid Move Target Indicators
        captureRingPaint.strokeWidth = cellSize * 0.08f
        for (m in validMoves) {
            val displayR = if (isFlipped) 7 - m.toR else m.toR
            val displayC = if (isFlipped) 7 - m.toC else m.toC

            val cx = displayC * cellSize + cellSize / 2f
            val cy = displayR * cellSize + cellSize / 2f

            val targetPiece = currentLogic?.board?.get(m.toR)?.get(m.toC)
            if (targetPiece != null || m.isEnPassant) {
                // Capture Ring on enemy piece
                val radius = cellSize * 0.42f
                canvas.drawCircle(cx, cy, radius, captureRingPaint)
            } else {
                // Subtle Center Dot on empty square
                val dotRadius = cellSize * 0.16f
                canvas.drawCircle(cx, cy, dotRadius, validDotPaint)
            }
        }

        // 3. Draw Pieces
        if (currentLogic != null) {
            val pieceFontSize = cellSize * 0.82f
            pieceWhitePaint.textSize = pieceFontSize
            pieceWhiteStrokePaint.textSize = pieceFontSize
            pieceWhiteStrokePaint.strokeWidth = cellSize * 0.035f

            pieceBlackPaint.textSize = pieceFontSize
            pieceBlackStrokePaint.textSize = pieceFontSize
            pieceBlackStrokePaint.strokeWidth = cellSize * 0.035f

            val yOffset = ((pieceWhitePaint.descent() + pieceWhitePaint.ascent()) / 2f)

            for (r in 0..7) {
                for (c in 0..7) {
                    val piece = currentLogic.board[r][c] ?: continue

                    val displayR = if (isFlipped) 7 - r else r
                    val displayC = if (isFlipped) 7 - c else c

                    val cx = displayC * cellSize + cellSize / 2f
                    val cy = displayR * cellSize + cellSize / 2f - yOffset

                    val symbol = piece.symbol

                    if (piece.color == PieceColor.WHITE) {
                        // White Piece with Stroke
                        canvas.drawText(symbol, cx, cy, pieceWhiteStrokePaint)
                        canvas.drawText(symbol, cx, cy, pieceWhitePaint)
                    } else {
                        // Black Piece (with optional stroke if configured)
                        if (pieceSkin.blackStrokeColor != null) {
                            canvas.drawText(symbol, cx, cy, pieceBlackStrokePaint)
                        }
                        canvas.drawText(symbol, cx, cy, pieceBlackPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cellSize = width / 8f
            if (cellSize <= 0) return true

            val clickedDisplayC = (event.x / cellSize).toInt().coerceIn(0, 7)
            val clickedDisplayR = (event.y / cellSize).toInt().coerceIn(0, 7)

            val actualR = if (isFlipped) 7 - clickedDisplayR else clickedDisplayR
            val actualC = if (isFlipped) 7 - clickedDisplayC else clickedDisplayC

            onSquareTapped?.invoke(actualR, actualC)
            return true
        }
        return true
    }
}
