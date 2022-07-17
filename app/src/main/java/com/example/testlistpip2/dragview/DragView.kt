package com.example.testlistpip2.dragview

import android.animation.Animator
import android.content.Context
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

    /**
     * 축소 사이즈
     */
    private val scaleSize = 2f

    /**
     * 클릭 민감도
     */
    private val clickSensitivity = 5f

    fun init(top: ViewGroup, bottom: ViewGroup, listener: IDragListener) {
        topView = top
        bottomView = bottom
        dragListener = listener

        onTouch()
    }

    private fun onTouch() {
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

                        if(isPipMode) {
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
                            Log.i(
                                "TEST",
                                "ACTION_MOVE check:($checkX, $checkY) -> x[$moveCheckX], y[$moveCheckY]"
                            )
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
                            Log.i("TEST", "ACTION_MOVE velocity:[$velocity], checkY:[$checkY]")
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
                                moveMax(v)
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
                                moveMax(v)
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
    fun moveMax(view: View) {
        isPipMode = false

        bottomView.visibility = View.VISIBLE
        bottomView.alpha = 1f
        bottomView.animate()
            .translationY(0f)
            .translationX(0f)
            .setDuration(100)
            .start()

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

    /**
     * 최소화
     */
    fun moveMin(parentView: View, view: View) {
        isPipMode = true
        bottomView.alpha = 0f
        bottomView.visibility = View.GONE

        var topHeight = topView.height.toFloat()
        var topWidth = topView.width.toFloat()

//                // 이동 (원래 사이즈만큼)
        moveY = parentView.height.toFloat() - topHeight
        moveX = parentView.width.toFloat() - topWidth

        // 줄어든 사이즈만큼 위치 조절 (비율로 줄어든 만큼 차이점을 계산) !! scale 하게되면 가운데로 줄어들어서 좌표값 계산을 반으로 나눠서 해야됨.
        moveY += (topHeight - (topHeight / scaleSize)) / 2
        moveX += (topWidth - (topWidth / scaleSize)) / 2
//                Log.i(
//                    "TEST",
//                    "moveMin() move:($moveX, $moveY) - height:$topHeight -> ${(topHeight - (topHeight / scaleSize)) / 2}, width:$topWidth -> ${(topWidth - (topWidth / scaleSize)) / 2}"
//                )

        // 사이즈 조절
        view.animate()
            .translationY(moveY)
            .translationX(moveX)
            .scaleX(1 / scaleSize)
            .scaleY(1 / scaleSize)
            .setDuration(100)
            .start()

        dragListener.onMinimized()
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
        var scaleSizeX = (view.width - (view.width / scaleSize)) / 2
        var scaleSizeY = (view.height - (view.height / scaleSize)) / 2

        // 선택한 이미지 정 가운데 좌표
        var centerX = (dragX + (view.width / 2))
        var centerY = (dragY + (view.height / 2))

        // 값 보정
        centerX -= scaleSizeX
        centerY -= scaleSizeY

        // 왼쪽, 오른쪽 구분
        var actionMoveX: Float = if (centerX < (parent.width / 2) - scaleSizeX) {
            Log.d("TEST", "actionMove 왼쪽")
            margin.toFloat() - scaleSizeX
        } else {
            Log.d("TEST", "actionMove 오른쪽")
            scaleSizeX - (margin).toFloat()
        }
        Log.d("TEST", "actionMove x : $actionMoveX")

        // 위, 아래 구분
        var actionMoveY: Float = if (centerY < (parent.height / 2) - scaleSizeY) {
            Log.d("TEST", "actionMove 위")
            -scaleSizeY + margin.toFloat()
        } else {
            Log.d("TEST", "actionMove 아래")
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

        // 줄어든 사이즈만큼 위치 조절 (비율로 줄어든 만큼 차이점을 계산)
        var scaleSizeX = (view.width - (view.width / scaleSize)) / 2

        // 값 보정 (정 좌표)
        viewPositionX -= scaleSizeX

        // 화면 밖으로 넘어가면 종료되는 최소 사이즈
        var checkOverSizeX = (view.width / scaleSize) / 2

        var actionMoveX = 0f

        if (viewPositionX < -checkOverSizeX) {
            // 왼쪽
            actionMoveX = -((view.width / 2) + checkOverSizeX)
            Log.d("TEST", "checkDragFinish() 왼쪽")
        } else if (view.width - checkOverSizeX < viewPositionX) {
            // 오른쪽
            actionMoveX = (view.width.toFloat())
            Log.d("TEST", "checkDragFinish() 오른쪽")
        } else {
            isFinish = false
        }

        if (isFinish) {
            view.animate()
                .translationX(actionMoveX)
                .setDuration(500)
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(a: Animator) {
                        Log.d("TEST", "onAnimationStart() a:[$a]")
                    }

                    override fun onAnimationEnd(a: Animator) {
                        Log.d("TEST", "onAnimationEnd() a:[$a]")
                        view.animate().setListener(null) // onAnimationEnd() 호출이 두번되는것을 막음
                        dragListener.onFinish()
                    }

                    override fun onAnimationCancel(a: Animator) {
                        Log.d("TEST", "onAnimationCancel() a:[$a.]")
                    }

                    override fun onAnimationRepeat(a: Animator) {
                        Log.d("TEST", "onAnimationRepeat() a:[$a]")
                    }
                })
                .start()
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
        moveMax(topView)
    }

    /**
     * 최소화 상태값 반환
     */
    fun isMinimized(): Boolean {
        return isPipMode
    }
}
