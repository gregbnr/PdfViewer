import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.GestureDetectorCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import java.io.File


class PDFViewer(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs) {

    private var pdfRenderer: PdfRenderer? = null
    private val bitmaps = mutableMapOf<Int, Bitmap>()
    private var currentPage = 0
    private lateinit var adapter: PDFPageAdapter
    private lateinit var viewPager: ViewPager
    private var currentScale = 1f
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())


    fun setPdfByteArray(bytes: ByteArray) {
        val parcelFileDescriptor = ParcelFileDescriptorUtil.fromByteArray(bytes)
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
        adapter = PDFPageAdapter(pdfRenderer!!, bitmaps)
        initViewPager()
    }

    private fun initViewPager() {
        viewPager = ViewPager(context)
        viewPager.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
        }
        viewPager.adapter = adapter
        addView(viewPager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }


    private fun getPageBitmap(pageIndex: Int): Bitmap {
        var bitmap = bitmaps[pageIndex]
        if (bitmap != null) {
            return bitmap
        }
        val page = pdfRenderer!!.openPage(pageIndex)
        val pageWidth = page.width * currentScale
        val pageHeight = page.height * currentScale
        bitmap = Bitmap.createBitmap(pageWidth.toInt(), pageHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(
            currentScale,
            currentScale
        ) // Utilisez la valeur d'échelle actuelle pour dessiner la page
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmaps[pageIndex] = bitmap
        page.close()
        return bitmap
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setOnClickListener {
            viewPager.currentItem = currentPage + 1
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pdfRenderer?.close()
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            currentScale *= detector.scaleFactor
            currentScale = currentScale.coerceIn(0.5f, 5f) // Limitez la valeur de zoom
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
            if (detector?.scaleFactor != 1f) {
                currentScale = 1f // Réinitialiser l'échelle à 1 si le zoom est terminé
                viewPager.currentItem = currentPage
            }
            super.onScaleEnd(detector)
        }
    }

    private inner class PDFPageAdapter(
        private val renderer: PdfRenderer,
        private val bitmaps: MutableMap<Int, Bitmap>
    ) : PagerAdapter() {

        override fun getCount(): Int {
            return renderer.pageCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val imageView = ZoomableImageView(container.context)
            imageView.setImageBitmap(getPageBitmap(position))
            container.addView(imageView)
            return imageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
            bitmaps.remove(position)
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view === obj
        }
    }
}

object ParcelFileDescriptorUtil {

    fun fromByteArray(byteArray: ByteArray): ParcelFileDescriptor {
        val file = File.createTempFile("temp", null)
        file.writeBytes(byteArray)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}

class ZoomableImageView(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs), GestureDetector.OnGestureListener,
    ScaleGestureDetector.OnScaleGestureListener {

    private val matrixValues = FloatArray(9)
    private val minScale = 0.5f
    private val maxScale = 5f
    private val gestureDetector = GestureDetectorCompat(context, this)
    private val scaleGestureDetector = ScaleGestureDetector(context, this)

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.concat(matrix)
        super.onDraw(canvas)
        canvas.restore()
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        val currentScale = getCurrentScale()
        val newScale = currentScale * scaleFactor
        val clampedScale = newScale.coerceIn(minScale, maxScale)
        val scaleRatio = clampedScale / currentScale
        matrix.postScale(scaleRatio, scaleRatio, detector.focusX, detector.focusY)
        invalidate()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        // Intentionally empty
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onShowPress(e: MotionEvent) {
        // Intentionally empty
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        performClick()
        return true
    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        matrix.postTranslate(-distanceX, -distanceY)
        invalidate()
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        // Intentionally empty
    }

    override fun onFling(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        return true
    }

    private fun getCurrentScale(): Float {
        matrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        imageMatrix.getValues(matrixValues)
        val handled = scaleGestureDetector.onTouchEvent(event)
        if (!handled) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }
}
