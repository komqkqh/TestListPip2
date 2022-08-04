package com.example.testlistpip2.dragview

import android.animation.Animator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import kotlin.math.abs

class DragView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet
) : ConstraintLayout(context, attrs) {

    private lateinit var topView: ViewGroup
    private lateinit var bottomView: ViewGroup

    private lateinit var dragListener: IDragListener

    // 드래그 변수 모음 (나중에 한곳에 몰아넣기 위해) ===
    var moveX = 0f
    var moveY = 0f

    /**
     * 가로 축소 사이즈
     * 가로폭 단말 짧은 쪽의 50%
     */
    private var scaleSize = PIP_SCALE_SIZE

    /**
     * 가로 세로마다 스케일 비율이 달라짐
     */
    private var scalePipY = 0f

    // 플레이어 세로버전 높이
    private val playerPortHeight = 250f // dp

    private var windowWidth = 0
    private var windowHeight = 0

    /**
     * 마진값
     */
    private val marginTop = dpToPx(context, 5f)
    private val marginLeft = dpToPx(context, 5f)
    private val marginRight = dpToPx(context, 5f)
    private val marginBottom = dpToPx(context, 5f)

    /**
     * 가속도 구하는녀석
     * https://injunech.tistory.com/154
     */
    private lateinit var tracker: VelocityTracker

    /**
     * 드래그 체크용 최대 스피드값 (이 값이 넘어가면 최소화 처리 시켜줌)
     */
    private val CHECK_DRAG_SPEED = 0.4

    /**
     * PIP 상태
     */
    private var isPipMode = false

    private var pipPosition = PIP_RIGHT_BOTTOM

    /**
     * 클릭 민감도
     */
    private val clickSensitivity = 10f

    private var orientation = 0

    fun init(top: ViewGroup, bottom: ViewGroup, listener: IDragListener) {
        topView = top
        bottomView = bottom
        dragListener = listener
        topView.bringToFront()

        getWindowSize()
        setOnTouch()
        setOrientation(resources.configuration.orientation)
        setTopLayout()
    }

    private fun setOrientation(orientation: Int) {
        this.orientation = orientation
    }

    /**
     * 가로 세로에 대한 비율을 재 조절해줌
     */
    private fun setTopLayout() {
        setScaleSize() // scale x
        setScaleY() // scale y
    }

    /**
     * Scale Size 를 가로 세로 판단해서 정해주기
     */
    private fun setScaleSize() {
        post {
            // 계산용 pip 변수
            var pipMinWidth = if (windowWidth < windowHeight) {
                (windowWidth * PIP_SCALE_SIZE).toInt()
            } else {
                (windowHeight * PIP_SCALE_SIZE).toInt()
            }

            scaleSize = pipMinWidth.toFloat() / (width.toFloat())

            dragLog(
                "setScaleSize() scaleSize:[$scaleSize], parentView($width, $height), window($windowWidth, $windowHeight), pipMinWidth:[$pipMinWidth], minWidth:[${(width) * scaleSize}]"
            )
        }
    }

    private fun setOnTouch() {
        tracker = VelocityTracker.obtain()
        // 속도 측정
        var velocity = 0f

        var velocityX = 0f
        var velocityY = 0f

        topView.setOnTouchListener(object : View.OnTouchListener {

            // 이동한 길이 값
            var moveCheckX = 0f
            var moveCheckY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                var parentView: View = v.parent as View

                tracker.addMovement(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragLog("ACTION_DOWN")
//                        moveX = v.x - event.rawX
                        moveY = v.y - event.rawY
                        moveX = v.x - event.rawX

                        moveCheckX = event.rawX + moveX
                        moveCheckY = event.rawY + moveY

                        if (isPipMode) {
                            dragListener.onPipDragingStart()
                        } else {
                            dragListener.onDragingStart()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {

                        // 드래그 가속도 측정
                        tracker.computeCurrentVelocity(1)

                        if (isPipMode) {

                            if (velocityX < abs(tracker.xVelocity)) {
                                velocityX = abs(tracker.xVelocity)
                            }
                            if (velocityY < abs(tracker.yVelocity)) {
                                velocityY = abs(tracker.yVelocity)
                            }

                            // pip 용 드래그만
                            var checkX = event.rawX + moveX
                            var checkY = event.rawY + moveY

                            v.animate()
                                .translationX(checkX)
                                .translationY(checkY)
                                .setDuration(0)
                                .start()
                        } else {
                            if (velocity < abs(tracker.yVelocity)) {
                                velocity = abs(tracker.yVelocity)
                            }

                            // 드래그 이동
                            var checkY = event.rawY + moveY
                            if (checkY < 0) {
                                checkY = 0f
                            }

                            v.animate()
                                .translationY(checkY)
                                .setDuration(0)
                                .start()

                            bottomView.animate()
                                .translationY(checkY)
                                .setDuration(0)
                                .start()
                        }
                    }
                    MotionEvent.ACTION_UP -> {

                        // 드래그 상태에 따라 위로 올릴지 내릴지 판단해줌
                        if (isPipMode) {
                            // 마지막 터치 위치
                            var checkX = event.rawX + moveX
                            var checkY = event.rawY + moveY

                            // 터치 오차범위 내에 있으면 클릭으로 인정
                            if (clickSensitivity > abs(checkX - moveCheckX) &&
                                clickSensitivity > abs(checkY - moveCheckY)
                            ) {
                                // 클릭
                                moveMaximized()
                            } else {
                                // 밖으로 내보내는 드래그 체크
                                if (!checkDragFinish(v)) {
                                    dragLog(
                                        "ACTION_UP 가속도 체크 velocityX:[$velocityX], velocityY:[$velocityY]"
                                    )
                                    dragLog(
                                        "ACTION_UP 거리 체크 x ${width / 2} < ${abs(checkX - moveCheckX)}, y ${height / 2} < ${
                                        abs(
                                            checkY - moveCheckY
                                        )
                                        }"
                                    )
                                    // 가속도 체크 (드래그 길이가 길면 드래그 이동 로직을 태운다, 가속도는 드래그 길이가 무조건 짧아야지 태운다.)
                                    if ((width / 2) > abs(checkX - moveCheckX) &&
                                        (height / 2) > abs(checkY - moveCheckY) &&
                                        (CHECK_DRAG_SPEED < velocityX || CHECK_DRAG_SPEED < velocityY)
                                    ) {
                                        // 가속도 이동
                                        actionUpMoveCheck(velocityX, velocityY)
                                    } else {
                                        // 4등분 화면 위치로 보내는 애니메이션
                                        actionUpMove(parentView, v)
                                    }
                                }
                            }

                            velocityX = 0f
                            velocityY = 0f
                            dragLog(
                                "ACTION_UP pip Mode x,y:(${parentView.x}, ${parentView.y})"
                            )
                        } else {
                            var checkResumeY = parentView.height / 3 // 1/3 위치 이상 넘어가면 드래그 되도록 체크
                            var checkY = event.rawY + moveY // 드래그 최소 범위 체크용
                            var minCheck =
                                (height / 10) < abs(checkY - moveCheckY) // 1/10 길이 보다 더 길게 드래그 해야지 최소 화면으로 넘어가도록
                            dragLog(
                                "ACTION_UP 최고 가속도:[$velocity] y:[${parentView.y}], checkResumeY:[$checkResumeY]"
                            )
                            // 드래그 복귀, 내리기 결정
                            if (minCheck && CHECK_DRAG_SPEED < velocity || v.y > checkResumeY) {
                                dragLog("ACTION_UP isPipMode:[$isPipMode] drag")
                                moveMinimized(parentView, v, false)
                            } else {
                                dragLog("ACTION_UP isPipMode:[$isPipMode] 복귀")
                                moveMaximized()
                            }
                        }
                        // 가속도 초기화
                        velocity = 0f
                    }
                }
                return true
            }
        })
    }

    /**
     * 최대화
     */
    private fun moveMaximized() {
        post {
            dragLogD("moveMaximized()")
            isPipMode = false

            setTopViewLayoutParams()

            // 사이즈 조절
            topView.animate()
                .translationY(0f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(100)
                .start()

            dragListener.onMaximized()
        }
    }

    /**
     * TopView 의 orientation 상황에 따라 크기를 조절
     */
    private fun setTopViewLayoutParams() {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            bottomView.visibility = View.GONE
            bottomView.alpha = 0f

            val params: ViewGroup.LayoutParams = topView.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            topView.layoutParams = params
            topView.requestLayout()
        } else {
            bottomView.visibility = View.VISIBLE
//            bottomView.alpha = 1f
            bottomView.animate()
                .translationY(0f)
                .translationX(0f)
                .setDuration(100)
                .alpha(1f)
                .start()

            val params: ViewGroup.LayoutParams = topView.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = dpToPx(context, playerPortHeight) // 세로 모드 높이 수정
            topView.layoutParams = params
            topView.requestLayout()
        }
    }

    /**
     * 최소화
     */
    private fun moveMinimized(parentView: View, view: View, isRotation: Boolean) {
        setTopViewLayoutParams()

        post {
            dragLogD("moveMin() 최소화")

            isPipMode = true
            bottomView.alpha = 0f
            bottomView.visibility = View.GONE

            var topWidth = topView.width.toFloat()
            var topHeight = topView.height.toFloat()

            // 이동 (원래 사이즈만큼)
            moveX = parentView.width.toFloat() - topWidth
            moveY = parentView.height.toFloat() - topHeight
            dragLog(
                "moveMin() move:($moveX, $moveY), parentView(${parentView.width}, ${parentView.height}), topView(${topView.width}, ${topView.height})"
            )

            // 줄어든 사이즈만큼 위치 조절 (비율로 줄어든 만큼 차이점을 계산) !! scale 하게되면 가운데로 줄어들어서 좌표값 계산을 반으로 나눠서 해야됨.
            moveX += (topWidth - (topWidth * scaleSize)) / 2
            moveY += (topHeight - (topHeight * scalePipY)) / 2

            // 마진값 처리
//            moveX -= margin
//            moveY -= margin

            moveX -= marginRight
            moveY -= marginBottom

            dragLog(
                "moveMin() move:($moveX, $moveY) - height:$topHeight -> ${(topHeight - (topHeight * scalePipY)) / 2}, width:$topWidth -> ${(topWidth - (topWidth * scaleSize)) / 2}, scaleSize:[$scaleSize], scaleY:[$scalePipY]"
            )
            dragLog(
                "moveMin() bottomView height:[${bottomView.height}]"
            )
            dragLog(
                "moveMin() pip size (${topWidth * scaleSize}, ${topHeight * scalePipY})"
            )

            // 사이즈 조절
            view.animate()
                .x(moveX)
                .y(moveY)
                .scaleX(scaleSize)
                .scaleY(scalePipY)
                .setDuration(100)
                .start()

            if (!isRotation) pipPosition = PIP_RIGHT_BOTTOM

            dragListener.onMinimized()
        }
    }

    /**
     * 4등분 화면으로 가속도 값을 통해 움직여주는 로직 처리
     */
    private fun actionUpMoveCheck(velocityX: Float, velocityY: Float) {
        dragLog(
            "actionUpMoveCheck() velocityX:[$velocityX], velocityY:[$velocityY], pipPosition:[$pipPosition]"
        )
        if (CHECK_DRAG_SPEED < velocityX || CHECK_DRAG_SPEED < velocityY) {
            if (velocityX < velocityY) {
                actionMoveCheckY()
            } else {
                actionMoveCheckX()
            }
        }

        dragLog(
            "actionUpMoveCheck() end -> pipPosition:[$pipPosition]"
        )

        movePipEdge()
    }

    /**
     * x 좌표 반대값 반환
     */
    private fun actionMoveCheckX() {
        when (pipPosition) {
            PIP_LEFT_TOP -> {
                pipPosition = PIP_RIGHT_TOP
            }
            PIP_RIGHT_TOP -> {
                pipPosition = PIP_LEFT_TOP
            }
            PIP_LEFT_BOTTOM -> {
                pipPosition = PIP_RIGHT_BOTTOM
            }
            PIP_RIGHT_BOTTOM -> {
                pipPosition = PIP_LEFT_BOTTOM
            }
        }
    }

    /**
     * Y 좌표 반대값 반환
     */
    private fun actionMoveCheckY() {
        when (pipPosition) {
            PIP_LEFT_TOP -> {
                pipPosition = PIP_LEFT_BOTTOM
            }
            PIP_RIGHT_TOP -> {
                pipPosition = PIP_RIGHT_BOTTOM
            }
            PIP_LEFT_BOTTOM -> {
                pipPosition = PIP_LEFT_TOP
            }
            PIP_RIGHT_BOTTOM -> {
                pipPosition = PIP_RIGHT_TOP
            }
        }
    }

    /**
     * 4등분 화면으로 이동시켜주는 애니메이션 처리
     */
    private fun actionUpMove(parent: View, view: View) {
        // 마진값
        var dragX = view.x
        var dragY = view.y

        // 원래 사이즈와 줄어든 사이즈의 거리 차이점
        var scaleSizeX = getScaleSizeX(view)
        var scaleSizeY = getScaleSizeY(view)

        // 선택한 이미지 정 가운데 좌표
        var centerX = (dragX + (view.width / 2))
        var centerY = (dragY + (view.height / 2))

        // 값 보정
        centerX -= scaleSizeX
        centerY -= scaleSizeY

        var left = false

        // 왼쪽, 오른쪽 구분
        var actionMoveX: Float = if (centerX < (parent.width / 2) - scaleSizeX) {
            dragLogD("actionMove 왼쪽")
            left = true
            marginLeft - scaleSizeX
        } else {
            dragLogD("actionMove 오른쪽")
            left = false
            scaleSizeX - marginRight
        }
        dragLogD("actionMove x : $actionMoveX")

        // 위, 아래 구분
        var actionMoveY: Float = if (centerY < (parent.height / 2) - scaleSizeY) {
            dragLogD("actionMove 위")
            if (left) {
                pipPosition = PIP_LEFT_TOP
            } else {
                pipPosition = PIP_RIGHT_TOP
            }
            -scaleSizeY + marginTop
        } else {
            dragLogD("actionMove 아래")
            if (left) {
                pipPosition = PIP_LEFT_BOTTOM
            } else {
                pipPosition = PIP_RIGHT_BOTTOM
            }
            (parent.height - view.height).toFloat() + scaleSizeY - marginBottom
        }
        dragLogD("actionMove y : $actionMoveY")

        dragLogD(
            "c:[$actionMoveX, $actionMoveY], x,y:[$dragX, $dragY], scaleSize:[$scaleSizeX, $scaleSizeY]"
        )

        SpringAnimation(
            view,
            DynamicAnimation.TRANSLATION_X,
            actionMoveX
        ).start() to SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, actionMoveY).start()
    }

    /**
     * 코너 위치로 이동
     */
    private fun movePipEdge() {
        post {
            // 원래 사이즈와 줄어든 사이즈의 거리 차이점
            var scaleSizeX = getScaleSizeX(topView)
            var scaleSizeY = getScaleSizeY(topView)

            var actionMoveX = 0f
            var actionMoveY = 0f

            when (pipPosition) {
                PIP_LEFT_TOP -> {
                    actionMoveX = marginLeft - scaleSizeX
                    actionMoveY = -scaleSizeY + marginTop
                }
                PIP_RIGHT_TOP -> {
                    actionMoveX = scaleSizeX - marginRight
                    actionMoveY = -scaleSizeY + marginTop
                }
                PIP_LEFT_BOTTOM -> {
                    actionMoveX = marginLeft - scaleSizeX
                    actionMoveY = (height - topView.height).toFloat() + scaleSizeY - marginBottom
                }
                PIP_RIGHT_BOTTOM -> {
                    actionMoveX = scaleSizeX - marginRight
                    actionMoveY = (height - topView.height).toFloat() + scaleSizeY - marginBottom
                }
            }

            SpringAnimation(
                topView,
                DynamicAnimation.TRANSLATION_X,
                actionMoveX
            ).start() to SpringAnimation(
                topView,
                DynamicAnimation.TRANSLATION_Y,
                actionMoveY
            ).start()
        }
    }

    /**
     * 원래 사이즈와 줄어든 사이즈의 거리 차이점 X 좌표
     */
    private fun getScaleSizeX(view: View): Float {
        return (view.width - (view.width * scaleSize)) / 2
    }

    /**
     * 원래 사이즈와 줄어든 사이즈의 거리 차이점 Y 좌표
     */
    private fun getScaleSizeY(view: View): Float {
        return (view.height - (view.height * scalePipY)) / 2
    }

    /**
     * 밖으로 드래그해서 종료시키는 메소드
     */
    private fun checkDragFinish(view: View): Boolean {
        var isFinish = true

        var moveX = view.x

        // 선택한 이미지 정 가운데 좌표
        var viewPositionX = (moveX + (view.width / 2))

        dragLogD(
            "checkDragFinish() viewPositionX : $viewPositionX = ($moveX + (${view.width} / 2))"
        )

        var actionMoveX = 0f

        if (viewPositionX < 0) {
            // 왼쪽
            actionMoveX = -(view.width - ((view.width / 2) - ((view.width * scaleSize) / 2)))
            dragLogD(
                "checkDragFinish() 왼쪽 viewPositionX:[$viewPositionX], actionMoveX:[$actionMoveX]"
            )
        } else if (view.width < viewPositionX) {
            // 오른쪽
            actionMoveX = view.width - ((view.width / 2) - ((view.width * scaleSize) / 2))
            dragLogD(
                "checkDragFinish() 오른쪽 viewPositionX:[$viewPositionX], actionMoveX:[$actionMoveX]"
            )
        } else {
            isFinish = false
        }

        if (isFinish) {
            // https://stackoverflow.com/questions/53612269/android-onanimationstart-and-onanimationend-are-getting-executed-twice-for-cust
            view.post {
                view.animate()
                    .setListener(object : Animator.AnimatorListener {

                        override fun onAnimationStart(p0: Animator?) {
                            dragLogD("onAnimationStart() $this")
                        }

                        override fun onAnimationEnd(a: Animator) {
                            dragLogD("onAnimationEnd() $this")
                            dragListener.onFinish()
                        }

                        override fun onAnimationCancel(p0: Animator?) {
                            dragLogD("onAnimationCancel()")
                        }

                        override fun onAnimationRepeat(p0: Animator?) {
                            dragLogD("onAnimationRepeat()")
                        }
                    })
                    .translationX(actionMoveX)
                    .setDuration(500)
                    .start()
            }
        }

        dragLogD(
            "checkDragFinish() : [$viewPositionX] actionMoveX:[$actionMoveX] moveX:[$moveX]"
        )
        return isFinish
    }

    /**
     * 최소화 시키기
     */
    fun setMinimized() {
        moveMinimized(this, topView, false)
    }

    /**
     * 최소화 화면 회전시 호출
     */
    private fun setMinimizedRotation() {
        moveMinimized(this, topView, true)
    }

    /**
     * 최대화 시키기
     */
    fun setMaximized() {
        moveMaximized()
    }

    /**
     * 최소화 상태값 반환
     */
    fun isMinimized(): Boolean {
        return isPipMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dragLogD(
            "onConfigurationChanged newConfig:[${newConfig.orientation}], isPipMode:[$isPipMode]"
        )
        setOrientation(newConfig.orientation)
        getWindowSize()
        setTopLayout()

        if (isPipMode) {
            setMinimizedRotation()
            movePipEdge()
        } else {
            setMaximized()
        }
    }

    /**
     * dp -> px
     */
    fun dpToPx(context: Context, dp: Float): Int {
        // Took from http://stackoverflow.com/questions/8309354/formula-px-to-dp-dp-to-px-android
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    /**
     * 상태바 높이 가져오기
     */
    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        var statusBarHeight = 0
        if (resourceId != 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        } else {
            if (context is Activity) {
                val rect = Rect()
                context.window.decorView.getWindowVisibleDisplayFrame(rect)
                statusBarHeight = if (rect.top > 0) rect.top else 0
            }
        }
        return statusBarHeight
    }

    /**
     * 윈도우 실제 사이즈 측정
     */
    private fun getWindowSize() {
        val size = Point()
        if (context is Activity) {
            (context as Activity).windowManager.defaultDisplay.getRealSize(size)
            windowWidth = size.x
            windowHeight = size.y
        }

        dragLog("getWindowSize() window($windowWidth, $windowHeight)")
    }

    /**
     * 가로에서 높이 축소를 위해 사이즈를 변경함
     */
    private fun setScaleY() {
        post {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                scalePipY = (
                    (dpToPx(context, playerPortHeight) / 2).toFloat() /
                        height
                    )
                dragLogD(
                    "scalePipY() $scalePipY = ${(dpToPx(context, playerPortHeight) / 2)} " +
                        "/ ($windowHeight - ${getStatusBarHeight(context)})"
                )
            } else {
                scalePipY = scaleSize
            }
            dragLog("setScaleY() scalePipY:[$scalePipY]")
        }
    }

    private fun dragLog(str: String) {
        Log.i(TAG, str)
    }

    private fun dragLogD(str: String) {
        Log.d(TAG, str)
    }

    companion object {
        private val TAG: String = DragView::class.java.simpleName
        private const val PIP_LEFT_TOP = 0
        private const val PIP_RIGHT_TOP = 1
        private const val PIP_LEFT_BOTTOM = 2
        private const val PIP_RIGHT_BOTTOM = 3
        private const val PIP_SCALE_SIZE = 0.5f
    }
}
