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
    private var scaleSize = 0.5f

    /**
     * 가로 세로마다 스케일 비율이 달라짐
     */
    private var scalePipX = 0f
    private var scalePipY = 0f

    // 플레이어 세로버전 높이
    private val playerPortHeight = 250f // dp

    // 계산용 pip 변수
    private var pipMinWidth = 0
    private var pipMinHeight = 0

    private var windowWidth = 0
    private var windowHeight = 0

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
    private val clickSensitivity = 5f

    private lateinit var activity: Activity

    private var orientation = 0

    fun init(activity: Activity, top: ViewGroup, bottom: ViewGroup, listener: IDragListener) {
        this.activity = activity
        topView = top
        bottomView = bottom
        dragListener = listener

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

        if (windowWidth < windowHeight) {
            topView.layoutParams.width = windowWidth
            pipMinWidth = windowWidth / 2
        } else {
            topView.layoutParams.width = windowHeight
            pipMinWidth = windowHeight / 2
        }

        scaleSize = (pipMinWidth.toFloat() / windowWidth.toFloat())

//        pipMinHeight = topView.height.toFloat()
        pipMinHeight = dpToPx(context, playerPortHeight) / 2
        setScaleY()

        Log.d(
            "TEST",
            "setTopLayout() display window($windowWidth, $windowHeight), pipMinWidth:[$pipMinWidth], scaleSize:[$scaleSize], scaleY:[$scalePipY], pipMinHeight:[$pipMinHeight]"
        )
    }

    private fun setOnTouch() {
        tracker = VelocityTracker.obtain()
        // 속도 측정
        var velocity = 0f

        topView.setOnTouchListener(object : View.OnTouchListener {

            // 이동한 길이 값
            var moveCheckX = 0f
            var moveCheckY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                var parentView: View = v.parent as View

                tracker.addMovement(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.i("TEST", "ACTION_DOWN")
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
                        if (isPipMode) {
                            // pip 용 드래그만
                            var checkX = event.rawX + moveX
                            var checkY = event.rawY + moveY

                            v.animate()
                                .translationX(checkX)
                                .translationY(checkY)
                                .setDuration(0)
                                .start()
//                            Log.i(
//                                "TEST",
//                                "ACTION_MOVE check:($checkX, $checkY) -> x[$moveCheckX], y[$moveCheckY]"
//                            )
                        } else {
                            // 드래그 가속도 측정
                            tracker.computeCurrentVelocity(1)
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
//                            Log.i("TEST", "ACTION_MOVE velocity:[$velocity], checkY:[$checkY]")
                        }
                    }
                    MotionEvent.ACTION_UP -> {

                        // 드래그 상태에 따라 위로 올릴지 내릴지 판단해줌
                        if (isPipMode) {
                            // 마지막 터치 위치
                            var checkX = event.rawX + moveX
                            var checkY = event.rawY + moveY

                            // 터치 오차범위 내에 있으면 클릭으로 인정
                            if (clickSensitivity > abs(checkX - moveCheckX) ||
                                clickSensitivity > abs(checkY - moveCheckY)
                            ) {
                                // 클릭
                                moveMax()
                            } else {
                                // 밖으로 내보내는 드래그 체크
                                if (!checkDragFinish(v)) {
                                    // 4등분 화면 위치로 보내는 애니메이션
                                    actionUpMove(parentView, v)
                                }
                            }
                            Log.i(
                                "TEST",
                                "ACTION_UP pip Mode x,y:(${parentView.x}, ${parentView.y})"
                            )
                        } else {
                            var checkResumeY = parentView.height / 3 // 1/3 위치 이상 넘어가면 드래그 되도록 체크
                            Log.i(
                                "TEST",
                                "ACTION_UP 최고 가속도:[$velocity] y:[${parentView.y}], checkResumeY:[$checkResumeY]"
                            )
                            // 드래그 복귀, 내리기 결정
                            if (CHECK_DRAG_SPEED < velocity || v.y > checkResumeY) {
                                Log.i("TEST", "ACTION_UP isPipMode:[$isPipMode] drag")
                                moveMin(parentView, v)
                            } else {
                                Log.i("TEST", "ACTION_UP isPipMode:[$isPipMode] 복귀")
                                moveMax()
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
    fun moveMax() {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            moveMaxLand(topView)
        } else {
            moveMaxPort(topView)
        }
    }

    /**
     * 세로형태 최대화
     */
    fun moveMaxPort(view: View) {
        post {
            Log.d("TEST", "moveMaxport()")
            isPipMode = false

            bottomView.visibility = View.VISIBLE
            bottomView.alpha = 1f
            bottomView.animate()
                .translationY(0f)
                .translationX(0f)
                .setDuration(100)
                .start()

            val params: ViewGroup.LayoutParams = view.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = dpToPx(context, playerPortHeight) // 세로 모드 높이 수정
            view.layoutParams = params
            view.requestLayout()

            // 사이즈 조절
            view.animate()
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
     * 가로형태 최대화
     */
    fun moveMaxLand(view: View) {
        post {
            Log.d("TEST", "moveMaxLand()")
            isPipMode = false

            bottomView.visibility = View.GONE
            bottomView.alpha = 0f

            val params: ViewGroup.LayoutParams = view.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = params
            view.requestLayout()

            // 사이즈 조절
            view.animate()
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
     * 최소화
     */
    fun moveMin(parentView: View, view: View) {
        post {
            Log.d("TEST", "moveMin() 최소화")
            isPipMode = true
            bottomView.alpha = 0f
            bottomView.visibility = View.GONE

            var topWidth = topView.width.toFloat()
            var topHeight = topView.height.toFloat()
//                dpToPx(context, playerPortHeight)
//                // 이동 (원래 사이즈만큼)
            moveX = parentView.width.toFloat() - topWidth
            moveY = parentView.height.toFloat() - topHeight
            Log.i(
                "TEST",
                "moveMin() move:($moveX, $moveY)"
            )
            // 줄어든 사이즈만큼 위치 조절 (비율로 줄어든 만큼 차이점을 계산) !! scale 하게되면 가운데로 줄어들어서 좌표값 계산을 반으로 나눠서 해야됨.
            moveX += (topWidth - (topWidth * scaleSize)) / 2
            moveY += (topHeight - (topHeight * scalePipY)) / 2

            // Y scale 사이즈 따로
            setScaleY()

            Log.i(
                "TEST",
                "moveMin() move:($moveX, $moveY) - height:$topHeight -> ${(topHeight - (topHeight * scaleSize)) / 2}, width:$topWidth -> ${(topWidth - (topWidth * scaleSize)) / 2}, scaleSize:[$scaleSize], scaleY:[$scalePipY]"
            )
            Log.i(
                "TEST",
                "moveMin() bottomView height:[${bottomView.height}]"
            )

            // 사이즈 조절
            view.animate()
                .translationY(moveY)
                .translationX(moveX)
                .scaleX(scaleSize)
                .scaleY(scalePipY)
                .setDuration(100)
                .start()

            dragListener.onMinimized()
        }
    }

    /**
     * 4등분 화면으로 이동시켜주는 애니메이션 처리
     */
    fun actionUpMove(parent: View, view: View) {
        // 마진값
        var margin = 0
        var dragX = view.x
        var dragY = view.y

        // 원래 사이즈와 줄어든 사이즈의 거리 차이점
        var scaleSizeX = (view.width - (view.width * scaleSize)) / 2
        var scaleSizeY = (view.height - (view.height * scalePipY)) / 2

        // 선택한 이미지 정 가운데 좌표
        var centerX = (dragX + (view.width / 2))
        var centerY = (dragY + (view.height / 2))

        // 값 보정
        centerX -= scaleSizeX
        centerY -= scaleSizeY

        var left = false

        // 왼쪽, 오른쪽 구분
        var actionMoveX: Float = if (centerX < (parent.width / 2) - scaleSizeX) {
            Log.d("TEST", "actionMove 왼쪽")
            left = true
            margin.toFloat() - scaleSizeX
        } else {
            Log.d("TEST", "actionMove 오른쪽")
            left = false
            scaleSizeX - (margin).toFloat()
        }
        Log.d("TEST", "actionMove x : $actionMoveX")

        // 위, 아래 구분
        var actionMoveY: Float = if (centerY < (parent.height / 2) - scaleSizeY) {
            Log.d("TEST", "actionMove 위")
            if (left) {
                pipPosition = PIP_LEFT_TOP
            } else {
                pipPosition = PIP_RIGHT_TOP
            }
            -scaleSizeY + margin.toFloat()
        } else {
            Log.d("TEST", "actionMove 아래")
            if (left) {
                pipPosition = PIP_LEFT_BOTTOM
            } else {
                pipPosition = PIP_RIGHT_BOTTOM
            }
            (parent.height - view.height).toFloat() + scaleSizeY - margin
        }
        Log.d("TEST", "actionMove y : $actionMoveY")

        Log.d(
            "TEST",
            "c:[$actionMoveX, $actionMoveY], x,y:[$dragX, $dragY], scaleSize:[$scaleSizeX, $scaleSizeY]"
        )

        SpringAnimation(
            view,
            DynamicAnimation.TRANSLATION_X,
            actionMoveX
        ).start() to SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, actionMoveY).start()
    }

    /**
     * 밖으로 드래그해서 종료시키는 메소드
     */
    fun checkDragFinish(view: View): Boolean {
        var isFinish = true

        var moveX = view.x

        // 선택한 이미지 정 가운데 좌표
        var viewPositionX = (moveX + (view.width / 2))

        Log.d(
            "TEST",
            "checkDragFinish() viewPositionX : $viewPositionX = ($moveX + (${view.width} / 2))"
        )

        var actionMoveX = 0f

        if (viewPositionX < 0) {
            // 왼쪽
            actionMoveX = -(view.width - ((view.width / 2) - ((view.width * scaleSize) / 2)))
            Log.d(
                "TEST",
                "checkDragFinish() 왼쪽 viewPositionX:[$viewPositionX], actionMoveX:[$actionMoveX]"
            )
        } else if (view.width < viewPositionX) {
            // 오른쪽
            actionMoveX = view.width - ((view.width / 2) - ((view.width * scaleSize) / 2))
            Log.d(
                "TEST",
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
                            Log.d("TEST", "onAnimationStart() $this")
                        }

                        override fun onAnimationEnd(a: Animator) {
                            Log.d("TEST", "onAnimationEnd() $this")
                            dragListener.onFinish()
                        }

                        override fun onAnimationCancel(p0: Animator?) {
                            Log.d("TEST", "onAnimationCancel()")
                        }

                        override fun onAnimationRepeat(p0: Animator?) {
                            Log.d("TEST", "onAnimationRepeat()")
                        }
                    })
                    .translationX(actionMoveX)
                    .setDuration(500)
                    .start()
            }
        }

        Log.d(
            "TEST",
            "checkDragFinish() : [$viewPositionX] actionMoveX:[$actionMoveX] moveX:[$moveX]"
        )
        return isFinish
    }

    /**
     * 최소화 시키기
     */
    fun setMinimized() {
        moveMin(this, topView)
    }

    /**
     * 최대화 시키기
     */
    fun setMaximized() {
        moveMax()
    }

    /**
     * 최소화 상태값 반환
     */
    fun isMinimized(): Boolean {
        return isPipMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(
            "TEST",
            "onConfigurationChanged newConfig:[${newConfig.orientation}], isPipMode:[$isPipMode]"
        )
        setOrientation(newConfig.orientation)
        setTopLayout()

        if (isPipMode) {
            setMinimized()
        } else {
            setMaximized()
        }
    }

    fun dpToPx(context: Context, dp: Float): Int {
        // Took from http://stackoverflow.com/questions/8309354/formula-px-to-dp-dp-to-px-android
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

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

    private fun getWindowSize() {
        val size = Point()
        activity.windowManager.defaultDisplay.getRealSize(size)
        windowWidth = size.x
        windowHeight = size.y
    }

    /**
     * 가로에서 높이 축소를 위해 사이즈를 변경함
     */
    private fun setScaleY() {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            scalePipY = (
                (dpToPx(context, playerPortHeight) / 2).toFloat() /
                    (windowHeight - getStatusBarHeight(context)).toFloat()
                )
            Log.d(
                "TEST",
                "scalePipY() $scalePipY = ${
                (
                    dpToPx(
                        context,
                        playerPortHeight
                    ) / 2
                    )
                } / ($windowHeight - ${getStatusBarHeight(context)})"
            )
        } else {
            scalePipY = scaleSize
        }
    }

    companion object {
        private const val PIP_LEFT_TOP = 0
        private const val PIP_RIGHT_TOP = 1
        private const val PIP_LEFT_BOTTOM = 2
        private const val PIP_RIGHT_BOTTOM = 3
    }
}
