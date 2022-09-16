package com.example.testlistpip2.dragview

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.*
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.example.testlistpip2.VodScreen
import com.example.testlistpip2.utils.NLog
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * DragView 리뉴얼 버전
 */
open class DragView constructor(
    context: Context,
    attrs: AttributeSet
) : ConstraintLayout(context, attrs) {

    protected lateinit var topView: View
    protected lateinit var bottomView: View

    private lateinit var dragListener: IDragViewListener

    /**
     * 현재 드래그 사용중인 플레이어
     */
    protected var dragPlayer = -1

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

    /**
     * 플레이어 세로버전 높이 (가변)
     * pip 모드일 경우 이 사이즈에 비율만 줄인것
     */
    protected var playerPortHeight = 250

    /**
     * PIP 최소 너비값
     */
    private var pipMinWidth = 0

    /**
     * 마진값
     */
    private val marginTop = dpToPx(context, 54f)
    private val marginLeft = dpToPx(context, 10f)
    private val marginRight = dpToPx(context, 10f)
    private val marginBottom = dpToPx(context, 112f)

    /**
     * 가속도 구하는녀석
     * https://injunech.tistory.com/154
     */
    private var tracker: VelocityTracker = VelocityTracker.obtain()

    /**
     * PIP 상태
     */
    protected var isPipMode = false

    private var _pipPosition = PIP_RIGHT_BOTTOM

    /**
     * PIP 4분할 선택된 위치
     */
    private var pipPosition
        set(value) {
            NLog.i(TAG, NLog.findClass("pipPosition:$value"))
            _pipPosition = value
        }
        get() = _pipPosition

    private var orientation = 0

    /**
     * 드래그 가능 상태(pip 가능 상태 유무)
     */
    var dragEnable = true

    /**
     * 화면 잠금 상태 (클릭, 드래그 전부 막힘)
     */
    var screenLock = false

    private var tapUpStartTime: Long = 0

    private val dragHandler: Handler = DragHandler()

    /**
     * Drag Moing State
     */
    private var dragMovingType = 0

    /**
     * 최소 터치 움찔(?!)범위
     */
    private val scrollSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * 직전 클릭이 롱 클릭이였을 경우
     */
    private var isLongClick = false

    /**
     * onInterceptTouchEvent 터치 UP 미 동작으로 인해 감지용으로 추가 (dispatchTouchEvent 에서는 감지됨)
     */
    private var actionIntercept = 0

    fun setData(
        listener: IDragViewListener
    ) {
        NLog.i(TAG, "setData()")
        dragListener = listener

        setOrientation(resources.configuration.orientation)

        setTopViewLayoutParams()
    }

    private fun isViewHit(view: View, x: Int, y: Int): Boolean {
        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val parentLocation = IntArray(2)
        getLocationOnScreen(parentLocation)
        val screenX = parentLocation[0] + x
        val screenY = parentLocation[1] + y
        return screenX >= viewLocation[0] && screenX < viewLocation[0] + view.width && screenY >= viewLocation[1] && screenY < viewLocation[1] + view.height
    }

    /**
     * PIP 시 화면 밖 영역 터치 감지 안되도록 처리
     * false 시 onInterceptTouchEvent 이벤트 동작 안함
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val returnEvent = super.dispatchTouchEvent(ev)
        val action = ev.actionMasked
        NLog.i(TAG, "dispatchTouchEvent() returnEvent:[$returnEvent], action:[$action]")
        if (action == MotionEvent.ACTION_DOWN && isPipMode) {
            if (!isViewHit(topView, ev.x.toInt(), ev.y.toInt())) {
                NLog.i(TAG, "dispatchTouchEvent() isViewHit:[false]")
                return false
            }
        } else if (action == MotionEvent.ACTION_UP && actionIntercept != MotionEvent.ACTION_UP) {
            // onInterceptTouchEvent 터치 UP 미 동작으로 인해 감지용으로 추가 (dispatchTouchEvent 에서는 감지됨)
            topViewOnTouchListener.onTouch(topView, ev)
        }
        return returnEvent
    }

    /**
     * 자식 뷰 터치 리스너를 자유롭게 사용하기 위해 onInterceptTouchEvent 로 구현
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val returnEvent = super.onInterceptTouchEvent(ev)
        NLog.i(
            TAG,
            "onInterceptTouchEvent() returnEvent:[$returnEvent], actionMasked:[${ev.actionMasked}]"
        )

        // 재생, 일시정지 눌렀을 경우 드래그뷰와 컨트롤러가 같이 터지되는 현상 분기처리
        if (findClickableViewInChild(this, ev.x.roundToInt(), ev.y.roundToInt())) {
            NLog.d("DragView", "onInterceptTouchEvent() findClickableViewInChild() true")
            return false
        }

        actionIntercept = ev.actionMasked
        topViewOnTouchListener.onTouch(topView, ev)

        return returnEvent
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isTopViewUnder = isViewUnder(topView, event.x.toInt(), event.y.toInt())
        NLog.d(TAG, "onTouchEvent() isTopViewUnder[$isTopViewUnder]")

        return super.onTouchEvent(event)
    }

    /**
     * 선택한 뷰 터치했는지 체크용
     */
    private fun isViewUnder(view: View?, x: Int, y: Int): Boolean {
        return if (view == null) {
            false
        } else x >= view.left && x < view.right && y >= view.top && y < view.bottom
    }

    private fun setOrientation(orientation: Int) {
        this.orientation = orientation
    }

    /**
     * 가로 세로에 대한 비율을 재 조절해줌
     */
    private fun setScale() {
        setScaleSize() // scale x
        setScaleY() // scale y
    }

    /**
     * 최소화 PIP 너비값
     */
    private fun setPipMinWidth() {
        val size = Point()
        context.display?.getRealSize(size)
        val windowWidth = size.x
        val windowHeight = size.y

        pipMinWidth = if (windowWidth < windowHeight) {
            (windowWidth * PIP_SCALE_SIZE).toInt()
        } else {
            (windowHeight * PIP_SCALE_SIZE).toInt()
        }

        NLog.i(TAG, "setPipMinWidth() pipMinWidth:[$pipMinWidth]")
    }

    /**
     * Scale Size 를 가로 세로 판단해서 정해주기
     */
    private fun setScaleSize() {
        post {
            // 계산용 pip 변수
            scaleSize = pipMinWidth.toFloat() / (width.toFloat())

            NLog.i(
                TAG,
                "setScaleSize() scaleSize:[$scaleSize], parentView($width, $height), pipMinWidth:[$pipMinWidth], minWidth:[${(width) * scaleSize}]"
            )
        }
    }

    /**
     * 상단 터치 리스너
     */
    private val topViewOnTouchListener: OnTouchListener = object : OnTouchListener {

        // 속도 측정
        var velocity = 0f
        var velocityX = 0f
        var velocityY = 0f

        // 이동한 길이 값
        var moveCheckX = 0f
        var moveCheckY = 0f

        var clickCheckX = 0f
        var clickCheckY = 0f

        var checkMoveStart = true // onPipDragingStart, onDragingStart 한번만 호출하도록 체크용

        /**
         * 최대화 화면 이동, PIP 모드일 경우 이동 및 클릭 제어
         */
        fun moveTouchEvent(v: View, event: MotionEvent) {
            if (!dragEnable || screenLock) {
                return
            }
            val parentView: View = v.parent as View

            tracker.addMovement(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    NLog.i(TAG, "ACTION_DOWN")
                    moveY = v.y - event.rawY
                    moveX = v.x - event.rawX

                    moveCheckX = event.rawX + moveX
                    moveCheckY = event.rawY + moveY
                }
                MotionEvent.ACTION_MOVE -> {

                    if (!isPipMode && !VodScreen.isHalf()) {
                        return
                    }

                    // 드래그 가속도 측정
                    tracker.computeCurrentVelocity(1)

                    if (isPipMode) {
                        if (abs(velocityX) < abs(tracker.xVelocity)) {
                            velocityX = tracker.xVelocity
                        }
                        if (abs(velocityY) < abs(tracker.yVelocity)) {
                            velocityY = tracker.yVelocity
                        }

                        // pip 용 드래그만
                        val checkX = event.rawX + moveX
                        val checkY = event.rawY + moveY

                        if (checkMoveStart) {
                            checkMoveStart = false
                            dragListener.onPipDragingStart()
                        }

                        NLog.i(TAG, "ACTION_MOVE isPipMode check[$checkX, $checkY]")

                        v.animate()
                            .translationX(checkX)
                            .translationY(checkY)
                            .setDuration(0)
                            .start()
                    } else {
                        val dy: Float = abs(event.y - clickCheckY)
                        // 최소 터치 움찔 범위
                        if (dy > scrollSlop) {
                            dragMovingType = DRAG_MOVING_Y
                        }

                        if (dragMovingType == DRAG_MOVING_Y) {
                            NLog.i(
                                TAG,
                                "moveTouchEvent() ACTION_MOVE scrollSlopPx ~~~ dy:[$dy], scrollSlopPx:[$scrollSlop]"
                            )
                            if (velocity < abs(tracker.yVelocity)) {
                                velocity = abs(tracker.yVelocity)
                            }

                            // 드래그 이동
                            var checkY = event.rawY + moveY
                            if (checkY < 0) {
                                checkY = 0f
                            }

                            // onDragingStart (한번만 호출하도록 체크)
                            if (checkMoveStart) {
                                checkMoveStart = false
                                dragListener.onDragingStart()
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
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_DOWN -> {

                    // 드래그 상태에 따라 위로 올릴지 내릴지 판단해줌
                    if (isPipMode) {
                        NLog.i(TAG, "ACTION_UP")

                        // 마지막 터치 위치
                        val checkX = event.rawX + moveX
                        val checkY = event.rawY + moveY

                        val dx: Float = abs(event.x - clickCheckX)
                        val dy: Float = abs(event.y - clickCheckY)

                        // 터치 오차범위 내에 있으면 클릭으로 인정
                        val moveXResult = abs(checkX - moveCheckX)
                        val moveYResult = abs(checkY - moveCheckY)

                        NLog.i(
                            TAG,
                            "ACTION_UP 1 드래그 체크용 -> dx,dy:($dx,$dy), moveTo:[${if (dx > scrollSlop && dx > dy) "x" else if (dy > scrollSlop) "y" else "click"}]"
                        )
                        NLog.i(
                            TAG,
                            "ACTION_UP 2 드래그 체크용 -> CLICK_SENSITIVITY:[$CLICK_SENSITIVITY], move x,y:($moveXResult, $moveYResult)"
                        )

                        if (CLICK_SENSITIVITY > abs(checkX - moveCheckX) &&
                            CLICK_SENSITIVITY > abs(checkY - moveCheckY)
                        ) {
                            // 클릭
                            moveMaximized()
                        } else {
                            // 밖으로 내보내는 드래그 체크
                            if (!checkDragFinish(v)) {

                                // PIP 이동 체크
                                NLog.i(
                                    TAG,
                                    "ACTION_UP 가속도 체크 velocityX:[$velocityX], velocityY:[$velocityY]"
                                )
                                NLog.i(
                                    TAG,
                                    "ACTION_UP 거리 체크 x ${width / 2} < $moveXResult, y ${height / 2} < $moveYResult"
                                )

                                // 가속도 체크 (드래그 길이가 길면 드래그 이동 로직을 태운다, 가속도는 드래그 길이가 무조건 짧아야지 태운다.)
                                if ((width / 2) > moveXResult &&
                                    (height / 2) > moveYResult &&
                                    (
                                        CHECK_DRAG_SPEED < abs(velocityX) || CHECK_DRAG_SPEED < abs(
                                                velocityY
                                            )
                                        )
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
                        NLog.i(TAG, "ACTION_UP pip Mode x,y:(${parentView.x}, ${parentView.y})")
                    } else {
                        val checkResumeY = parentView.height / 3 // 1/3 위치 이상 넘어가면 드래그 되도록 체크
                        val checkY = event.rawY + moveY // 드래그 최소 범위 체크용
                        // 1/10 길이 보다 더 길게 드래그 해야지 최소 화면으로 넘어가도록
                        val minCheck = (height / 10) < abs(checkY - moveCheckY)
                        NLog.i(
                            TAG,
                            "ACTION_UP 최고 가속도:[$velocity] y:[${parentView.y}], checkResumeY:[$checkResumeY]"
                        )
                        // 드래그 복귀, 내리기 결정
                        if (dragMovingType == DRAG_MOVING_Y) {
                            if (minCheck && CHECK_DRAG_SPEED < velocity || v.y > checkResumeY) {
                                NLog.i(TAG, "ACTION_UP isPipMode:[$isPipMode] drag")
                                moveMinimized(
                                    parentView = parentView,
                                    view = v,
                                    isRotation = false,
                                    isListener = true
                                )
                            } else {
                                NLog.i(TAG, "ACTION_UP isPipMode:[$isPipMode] 복귀")
                                moveMaximized()
                            }
                        }
                    }

                    velocity = 0f // 가속도 초기화
                    checkMoveStart = true // 움직임 체크용 초기화
                }
            }
        }

        /**
         * 클릭만 제어
         */
        fun clickTouchEvent(v: View, event: MotionEvent) {
            if (isPipMode) {
                return
            }
            val x: Float = event.x
            val y: Float = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    clickCheckX = x
                    clickCheckY = y
                    NLog.i(TAG, "clickTouchEvent() ACTION_DOWN")
                    removeLongTab()
                    delayedLongTab()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx: Float = abs(x - clickCheckX)
                    val dy: Float = abs(y - clickCheckY)
                    if (dx > scrollSlop && dx > dy) {
                        NLog.i(TAG, "clickTouchEvent() ACTION_MOVE scrollSlopPx 1")
                        removeLongTab()
                    } else if (dy > scrollSlop) {
                        NLog.i(TAG, "clickTouchEvent() ACTION_MOVE scrollSlopPx 2")
                        removeLongTab()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx: Float = abs(x - clickCheckX)
                    val dy: Float = abs(y - clickCheckY)
                    NLog.i(
                        TAG,
                        "clickTouchEvent() ACTION_UP d($dx, $dy), clickCheckY:[$clickCheckY] isLongClick:[$isLongClick], scrollSlopPx:[$scrollSlop]"
                    )
                    if (dx < scrollSlop && dy < scrollSlop) {
                        removeLongTab()
                        removeSingleTab()
                        if (isLongClick) {
                            isLongClick = false
                        } else {
                            // 더블 탭 체크
                            var isDbTap = false
                            val parentView: View = v.parent as View

                            NLog.i(
                                TAG,
                                "clickTouchEvent() ACTION_UP tapUpStartTime:[$tapUpStartTime] "
                            )

                            if (System.currentTimeMillis() - tapUpStartTime < DEFAULT_SINGLE_TAP_SEC && !screenLock) {
                                isDbTap = true
                                val widthQuarter = parentView.measuredWidth / 3
                                if (x < widthQuarter) {
                                    dragListener.onDoubleTab(true) // 왼쪽
                                } else if (x > widthQuarter * 2) {
                                    dragListener.onDoubleTab(false) // 오른쪽
                                }
                                tapUpStartTime = 0
                            }

                            if (!isDbTap) {
                                // LIVE 바로 클릭 되도록
                                if (dragPlayer == DRAG_LIVE_PLAYER) {
                                    dragListener.onClick()
                                } else {
                                    // VOD 중간은 바로 클릭 되도록
                                    val widthQuarter = parentView.measuredWidth / 3
                                    if (x < widthQuarter || x > widthQuarter * 2) {
                                        delayedSingleTab()
                                    } else {
                                        dragListener.onClick()
                                    }
                                }

                                tapUpStartTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            NLog.d("DragView", "onTouch() ::: [${event.actionMasked}]")

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    clickCheckX = event.x
                    clickCheckY = event.y

                    // TopView 를 눌렀을 경우에 상태값을 바꿔줌
                    dragMovingType = if (isViewUnder(topView, event.x.toInt(), event.y.toInt())) {
                        DRAG_MOVING_NO
                    } else {
                        DRAG_MOVING_UNKNOWN
                    }
                }
            }

            if (dragMovingType != DRAG_MOVING_UNKNOWN || isPipMode) {
                moveTouchEvent(v, event)
                clickTouchEvent(v, event)
            }

            return true
        }
    }

    /**
     * 최대화
     */
    private fun moveMaximized() {
        NLog.d(TAG, "moveMaximized()")
        isPipMode = false
        topView.clipToOutline = false
        setTopViewLayoutParams()

        topView.animate()
            .translationY(0f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .start()

        dragListener.onMaximized()
    }

    /**
     * TopView 의 orientation 상황에 따라 Layout 사이즈 조절
     */
    private fun setTopViewLayoutParams() {
        NLog.i(TAG, "setTopViewLayoutParams() orientation:[$orientation], isPipMode:[$isPipMode]")

        playerPortHeight = getPlayerHeight() // 높이값 재 산정
        setPipMinWidth()
        setScale()

        if (orientation == Configuration.ORIENTATION_LANDSCAPE && !isPipMode) {
            bottomView.visibility = View.GONE

            val params: ViewGroup.LayoutParams = topView.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            topView.layoutParams = params
        } else {
            if (!isPipMode) {
                bottomView.visibility = View.VISIBLE
            } else {
                bottomView.visibility = View.GONE
            }

            bottomView.animate()
                .translationY(0f)
                .translationX(0f)
                .setDuration(100)
                // .alpha(1f)
                .start()

            val params: ViewGroup.LayoutParams = topView.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = playerPortHeight // 세로 모드 높이 수정
            topView.layoutParams = params
        }
        topView.requestLayout()
    }

    /**
     * 최소화
     *
     * @param parentView DragView
     * @param view TopView
     * @param isRotation 최소화 회전으로 인한 호출 시
     * @param isListener 리스너 재 호출 안되도록 분기처리
     */
    private fun moveMinimized(
        parentView: View = this,
        view: View = topView,
        isRotation: Boolean = false,
        isListener: Boolean = false
    ) {
        NLog.d(TAG, "moveMinimized()")

        setTopViewLayoutParams()

        post {
            isPipMode = true
            topView.clipToOutline = true
            bottomView.visibility = View.GONE

            val topWidth = topView.width.toFloat()
            val topHeight = topView.height.toFloat()

            // 이동 (원래 사이즈만큼)
            moveX = parentView.width.toFloat() - topWidth
            moveY = parentView.height.toFloat() - topHeight
            NLog.i(
                TAG,
                "moveMin() move:($moveX, $moveY), parentView(${parentView.width}, ${parentView.height}), topView(${topView.width}, ${topView.height})"
            )

            // 줄어든 사이즈만큼 위치 조절 (비율로 줄어든 만큼 차이점을 계산) !! scale 하게되면 가운데로 줄어들어서 좌표값 계산을 반으로 나눠서 해야됨.
            moveX += (topWidth - (topWidth * scaleSize)) / 2
            moveY += (topHeight - (topHeight * scalePipY)) / 2

            moveX -= marginRight
            moveY -= marginBottom

            NLog.i(
                TAG,
                "moveMin() move:($moveX, $moveY) - height:$topHeight -> ${(topHeight - (topHeight * scalePipY)) / 2}, width:$topWidth -> ${(topWidth - (topWidth * scaleSize)) / 2}, scaleSize:[$scaleSize], scaleY:[$scalePipY]"
            )
            NLog.i(TAG, "moveMin() bottomView height:[${bottomView.height}]")

            val pipWidth = topWidth * scaleSize
            val pipHeight = topHeight * scalePipY

            NLog.i(
                TAG,
                "moveMin() pip size ($pipWidth, $pipHeight), scale x,y ($scaleSize, $scalePipY)"
            )
            NLog.i(
                TAG,
                "moveMin() view size ($pipWidth, $pipHeight), scale x,y ($scaleSize, $scalePipY)"
            )
            if (!isRotation) {
                // 회전 안하고 max -> min 이동
                view.animate()
                    .x(moveX)
                    .y(moveY)
                    .scaleX(scaleSize)
                    .scaleY(scalePipY)
                    .setDuration(100)
                    .start()

                pipPosition = PIP_RIGHT_BOTTOM
            } else {
                // 회전할때
                view.animate()
                    // .x(moveX)
                    // .y(moveY)
                    .scaleX(scaleSize)
                    .scaleY(scalePipY)
                    .setDuration(0)
                    .start()

                movePipEdge()
            }

            if (isListener) {
                dragListener.onMinimized()
            }
        }
    }

    /**
     * 4등분 화면으로 가속도 값을 통해 움직여주는 로직 처리
     */
    private fun actionUpMoveCheck(velocityX: Float, velocityY: Float) {
        NLog.i(
            TAG,
            "actionUpMoveCheck() velocityX:[$velocityX], velocityY:[$velocityY], pipPosition:[$pipPosition]"
        )

        if (CHECK_DRAG_SPEED < abs(velocityX)) {
            actionMoveCheckX(velocityX < 0)
        }

        if (CHECK_DRAG_SPEED < abs(velocityY)) {
            actionMoveCheckY(velocityY < 0)
        }

        NLog.i(
            TAG,
            "actionUpMoveCheck() end -> pipPosition:[$pipPosition]"
        )

        movePipEdge()
    }

    /**
     * x 좌표 반대값 반환
     * left = 왼쪽으로 변경
     */
    private fun actionMoveCheckX(left: Boolean) {
        NLog.i(TAG, "actionMoveCheckX() left:[$left]")
        when (pipPosition) {
            PIP_LEFT_TOP -> {
                if (!left) {
                    pipPosition = PIP_RIGHT_TOP
                }
            }
            PIP_RIGHT_TOP -> {
                if (left) {
                    pipPosition = PIP_LEFT_TOP
                }
            }
            PIP_LEFT_BOTTOM -> {
                if (!left) {
                    pipPosition = PIP_RIGHT_BOTTOM
                }
            }
            PIP_RIGHT_BOTTOM -> {
                if (left) {
                    pipPosition = PIP_LEFT_BOTTOM
                }
            }
        }
    }

    /**
     * Y 좌표 반대값 반환
     * top = 위로 변경
     */
    private fun actionMoveCheckY(top: Boolean) {
        NLog.i(TAG, "actionMoveCheckY() top:[$top]")
        when (pipPosition) {
            PIP_LEFT_TOP -> {
                if (!top) {
                    pipPosition = PIP_LEFT_BOTTOM
                }
            }
            PIP_RIGHT_TOP -> {
                if (!top) {
                    pipPosition = PIP_RIGHT_BOTTOM
                }
            }
            PIP_LEFT_BOTTOM -> {
                if (top) {
                    pipPosition = PIP_LEFT_TOP
                }
            }
            PIP_RIGHT_BOTTOM -> {
                if (top) {
                    pipPosition = PIP_RIGHT_TOP
                }
            }
        }
    }

    /**
     * 4등분 화면으로 이동시켜주는 애니메이션 처리
     */
    protected fun actionUpMove(parent: View, view: View) {
        NLog.d(TAG, "actionUpMove()")
        // 마진값
        val dragX = view.x
        val dragY = view.y

        // 원래 사이즈와 줄어든 사이즈의 거리 차이점
        val scaleSizeX = getScaleSizeX(view)
        val scaleSizeY = getScaleSizeY(view)

        // 선택한 이미지 정 가운데 좌표
        var centerX = (dragX + (view.width / 2))
        var centerY = (dragY + (view.height / 2))

        // 값 보정
        centerX -= scaleSizeX
        centerY -= scaleSizeY

        var left: Boolean

        // 왼쪽, 오른쪽 구분
        val actionMoveX: Float = if (centerX < (parent.width / 2) - scaleSizeX) {
            NLog.d(TAG, "actionMove 왼쪽")
            left = true
            marginLeft - scaleSizeX
        } else {
            NLog.d(TAG, "actionMove 오른쪽")
            left = false
            scaleSizeX - marginRight
        }
        NLog.d(TAG, "actionMove x : $actionMoveX")

        // 위, 아래 구분
        val actionMoveY: Float = if (centerY < (parent.height / 2) - scaleSizeY) {
            NLog.d(TAG, "actionMove 위")
            pipPosition = if (left) {
                PIP_LEFT_TOP
            } else {
                PIP_RIGHT_TOP
            }
            -scaleSizeY + marginTop
        } else {
            NLog.d(TAG, "actionMove 아래")
            pipPosition = if (left) {
                PIP_LEFT_BOTTOM
            } else {
                PIP_RIGHT_BOTTOM
            }
            (parent.height - view.height).toFloat() + scaleSizeY - marginBottom
        }
        NLog.d(TAG, "actionMove y : $actionMoveY")

        NLog.d(
            TAG,
            "c:[$actionMoveX, $actionMoveY], x,y:[$dragX, $dragY], scaleSize:[$scaleSizeX, $scaleSizeY]"
        )

        SpringAnimation(
            view,
            DynamicAnimation.TRANSLATION_X,
            actionMoveX
        ).start() to SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, actionMoveY).start()
    }

    /**
     * pipPosition 값으로 지정된 모서리 위치로 이동
     */
    private fun movePipEdge() {
        NLog.i(TAG, "movePipEdge() pipPosition:[$pipPosition]")
        // 원래 사이즈와 줄어든 사이즈의 거리 차이점
        val scaleSizeX = getScaleSizeX(topView)
        val scaleSizeY = getScaleSizeY(topView)

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

        val moveX = view.x

        // 선택한 이미지 정 가운데 좌표
        val viewPositionX = (moveX + (view.width / 2))

        NLog.d(
            TAG,
            "checkDragFinish() viewPositionX : $viewPositionX = ($moveX + (${view.width} / 2))"
        )

        var actionMoveX = 0f

        if (viewPositionX < 0) {
            // 왼쪽
            actionMoveX = -(view.width - ((view.width / 2) - ((view.width * scaleSize) / 2)))
            NLog.d(
                TAG,
                "checkDragFinish() 왼쪽 viewPositionX:[$viewPositionX], actionMoveX:[$actionMoveX]"
            )
        } else if (view.width < viewPositionX) {
            // 오른쪽
            actionMoveX = view.width - ((view.width / 2) - ((view.width * scaleSize) / 2))
            NLog.d(
                TAG,
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

                        override fun onAnimationStart(p0: Animator) {
                            NLog.d(TAG, "onAnimationStart() $this")
                        }

                        override fun onAnimationEnd(a: Animator) {
                            NLog.d(TAG, "onAnimationEnd() $this")
                            dragListener.onFinish()
                        }

                        override fun onAnimationCancel(p0: Animator) {
                            NLog.d(TAG, "onAnimationCancel()")
                        }

                        override fun onAnimationRepeat(p0: Animator) {
                            NLog.d(TAG, "onAnimationRepeat()")
                        }
                    })
                    .translationX(actionMoveX)
                    .setDuration(PIP_DRAG_FINISH_DURATION)
                    .start()
            }
        }

        NLog.d(
            TAG,
            "checkDragFinish() : [$viewPositionX] actionMoveX:[$actionMoveX] moveX:[$moveX]"
        )
        return isFinish
    }

    /**
     * 최소화 시키기
     */
    fun setMinimized(isRotation: Boolean = false, isListener: Boolean = false) {
        NLog.d(TAG, NLog.findClass("setMinimized() isListener:[$isListener]"))
        moveMinimized(isRotation = isRotation, isListener = isListener)
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
        NLog.d(
            TAG,
            "onConfigurationChanged newConfig:[${newConfig.orientation}], isPipMode:[$isPipMode]"
        )
        setOrientation(newConfig.orientation)

        if (isPipMode) {
            moveMinimized(isRotation = true, isListener = true)
        } else {
            moveMaximized()
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
    fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        var statusBarHeight = 0
        if (resourceId != 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        } else {
            if (context is Activity) {
                val rect = Rect()
                (context as Activity).window.decorView.getWindowVisibleDisplayFrame(rect)
                statusBarHeight = if (rect.top > 0) rect.top else 0
            }
        }
        return statusBarHeight
    }

    /**
     * 가로에서 높이 축소를 위해 사이즈를 변경함
     */
    private fun setScaleY() {
        post {
            scalePipY = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                (playerPortHeight / 2).toFloat() / playerPortHeight.toFloat()
            } else {
                scaleSize
            }
            NLog.i(
                TAG,
                "setScaleY() scalePipY:[$scalePipY], playerPortHeight:[$playerPortHeight], height:[$height], getStatusBarHeight:[${getStatusBarHeight()}]"
            )
        }
    }

    /**
     * 클릭 체크를 위한 핸들러
     */
    @SuppressLint("HandlerLeak")
    inner class DragHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            when (msg.what) {
                MSG_SINGLE_TAP -> {
                    NLog.i(TAG, "MSG_SINGLE_TAP !!!!!")
                    dragListener.onClick()
                }
                MSG_LONG_TAP -> {
                    NLog.i(TAG, "MSG_LONG_TAP !!!!!")
                    isLongClick = true
                    dragListener.onLongClick()
                }
            }
        }
    }

    /**
     * 싱글탭 딜레이 시작
     */
    private fun delayedSingleTab() {
        // NLog.i(TAG, "delayedSingleTab() 싱글 클릭 시작")
        dragHandler.sendEmptyMessageDelayed(MSG_SINGLE_TAP, DEFAULT_SINGLE_TAP_SEC)
    }

    /**
     * 싱글탭 종료
     */
    private fun removeSingleTab() {
        // NLog.i(TAG, "removeSingleTab() 싱글 클릭 제거")
        if (dragHandler.hasMessages(MSG_SINGLE_TAP)) {
            dragHandler.removeMessages(MSG_SINGLE_TAP)
        }
    }

    /**
     * 롱 클릭 딜레이 제거
     */
    protected fun removeLongTab() {
        // NLog.i(TAG, "removeLongTab() 롱 클릭 제거")
        if (dragHandler.hasMessages(MSG_LONG_TAP)) {
            dragHandler.removeMessages(MSG_LONG_TAP)
        }
    }

    /**
     * 롱 클릭 딜레이 시작
     */
    private fun delayedLongTab() {
        // NLog.i(TAG, "delayedLongTab() 롱 클릭 딜레이 시작")
        dragHandler.sendEmptyMessageDelayed(MSG_LONG_TAP, DEFAULT_LONG_TAP_SEC)
    }

    /**
     * 하단 뷰 높이 가져오기
     */
    fun getBottomViewHeight(): Int {
        return bottomView.height
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setDragTopView(view: View) {
        NLog.i(TAG, "setTopView()")
        topView = view
        topView.clipToOutline = false
        /**
         * TopView의 터치리스너 등록을 하지 않을 경우 RadioMode 에서 onInterceptTouchEvent() 동작이 멈춘다.
         * (왜인지는 모르겠으나 TouchEvent 를 다른 view에 뺏기는상황)
         * - 두근
         */
        topView.setOnTouchListener { _, _ -> true }
        topView.bringToFront()
    }

    fun setDragBottomVuew(view: View) {
        bottomView = view
    }

    private fun findClickableViewInChild(view: View, x: Int, y: Int): Boolean {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.visibility == VISIBLE) {
                    val rect = Rect()
                    child.getHitRect(rect)
                    val contains = rect.contains(x, y)
                    if (contains) {
                        if (child is ViewGroup) {
                            if (child.isEnabled() && child.isClickable()) {
                                return true
                            }
                            if (findClickableViewInChild(
                                    child,
                                    x - rect.left,
                                    y - rect.top
                                )
                            ) return true
                        } else if (child is SeekBar) {
                            return true
                        } else {
                            if (child.isEnabled && (child.isClickable || child.isLongClickable || child.isFocusableInTouchMode)) return true
                        }
                    }
                }
            }
        } else if (view.visibility == VISIBLE) {
            val rect = Rect()
            view.getHitRect(rect)
            val contains = rect.contains(x, y)
            if (contains) {
                return view.isEnabled && (view.isClickable || view.isLongClickable || view.isFocusableInTouchMode)
            }
        }
        return false
    }

    /**
     * window 너비값 가져와 다시 높이값 9/16 비율로 계산해준다
     */
    fun getPlayerHeight(): Int {
        val size = Point()
        context.display?.getRealSize(size)
        val windowWidth = size.x
        val windowHeight = size.y
        val playerHeight = if (windowWidth < windowHeight) {
            windowWidth * 9 / 16
        } else {
            windowHeight * 9 / 16
        }
        NLog.i(
            TAG,
            "getPlayerHeight() window w,h($windowWidth, $windowHeight) playerHeight[$playerHeight]"
        )
        return playerHeight
    }

    companion object {
        private val TAG: String = DragView::class.java.simpleName
        private const val PIP_LEFT_TOP = 0
        private const val PIP_RIGHT_TOP = 1
        private const val PIP_LEFT_BOTTOM = 2
        private const val PIP_RIGHT_BOTTOM = 3
        private const val PIP_SCALE_SIZE = 0.5f

        const val DEFAULT_SINGLE_TAP_SEC = 250L
        const val DEFAULT_LONG_TAP_SEC = 1002L

        const val MSG_SINGLE_TAP = 10
        const val MSG_LONG_TAP = 11

        private const val DRAG_MOVING_UNKNOWN = -1
        private const val DRAG_MOVING_NO = 0
        private const val DRAG_MOVING_Y = 1

        const val DRAG_LIVE_PLAYER = 0 // LIVE Player
        const val DRAG_VOD_PLAYER = 1 // VOD Player

        /**
         * 드래그 체크용 최대 스피드값 (이 값이 넘어가면 최소화 처리 시켜줌)
         */
        private const val CHECK_DRAG_SPEED = 3.5f

        /**
         * 클릭 민감도
         */
        private const val CLICK_SENSITIVITY = 10f

        /**
         * PIP 드래그 종료 애니메이션 속도 ms
         */
        private const val PIP_DRAG_FINISH_DURATION = 350L
    }
}
