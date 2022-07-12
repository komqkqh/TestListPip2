package com.example.testlistpip2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.testlistpip2.databinding.FragmentPlayerViewBinding
import kotlin.math.abs

class PlayerViewFragment : Fragment() {

    private lateinit var binding: FragmentPlayerViewBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPlayerViewBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onPlayerTouchMotion()
        binding.btn1.setOnClickListener {
            Log.i("TEST", "CLICK 1")
        }
        binding.btn2.setOnClickListener {
            Log.i("TEST", "CLICK 2")
        }
        binding.btn3.setOnClickListener {
            Log.i("TEST", "CLICK 3")
        }
    }

    // 드래그 변수 모음 (나중에 한곳에 몰아넣기 위해) ===
    var moveX = 0f
    var moveY = 0f

    /**
     * 가속도 구하는녀석
     * https://injunech.tistory.com/154
     */
    private lateinit var tracker: VelocityTracker

    /**
     * 드래그 페이징 체크용 최대 스피드값 (이 값이 넘어가면 페이징 처리 시켜줌)
     */
    private val CHECK_DRAG_SPEED = 0.4

    /**
     * PIP 상태
     */
    private var isPipMode = false

    /**
     * 축소 사이즈
     */
    private var scaleSize = 2f

    /**
     * 1. 위 아래 드래그 가능 (복귀도 해야됨)
     * 2. pip 모드로 화면 줄이기
     * 3. pip 상태로 화면 drag 가능
     */
    private fun onPlayerTouchMotion() {
        tracker = VelocityTracker.obtain()
        // 속도 측정
        var velocity = 0f
        binding.flTop.setOnTouchListener(object : View.OnTouchListener {

            override fun onTouch(v: View, event: MotionEvent): Boolean {

                var parentView: View = v.parent as View

                tracker.addMovement(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.i("TEST", "ACTION_DOWN")
//                        moveX = v.x - event.rawX
                        moveY = parentView.y - event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {

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
                        parentView.animate()
//                            .x(event.rawX + moveX)
                            .y(checkY)
                            .setDuration(0)
                            .start()

                        Log.i("TEST", "ACTION_MOVE velocity:[$velocity], checkY:[$checkY]")
                    }
                    MotionEvent.ACTION_UP -> {
                        var checkResumeY = parentView.height / 3
                        Log.i("TEST", "ACTION_UP 최고 가속도:[$velocity] y:[${parentView.y}], checkResumeY:[$checkResumeY]")

                        // 드래그 상태에 따라 위로 올릴지 내릴지 판단해줌
                        if (isPipMode) {
                            if (CHECK_DRAG_SPEED < velocity || parentView.y < (checkResumeY * 2)) {
                                Log.i("TEST", "ACTION_UP isPipMode:[$isPipMode] drag")
                                moveMax(parentView, v)
                            } else {
                                Log.i("TEST", "ACTION_UP isPipMode:[$isPipMode] 복귀")
                                moveMin(parentView, v)
                            }
                        } else {
                            // 드래그 복귀, 내리기 결정
                            if (CHECK_DRAG_SPEED < velocity || parentView.y > checkResumeY) {
                                Log.i("TEST", "ACTION_UP isPipMode:[$isPipMode] drag")
                                moveMin(parentView, v)
                            } else {
                                Log.i("TEST", "ACTION_UP isPipMode:[$isPipMode] 복귀")
                                moveMax(parentView, v)
                            }
                        }

                        setPipState(isPipMode)
                        // 가속도 초기화
                        velocity = 0f
                    }
                }
                return true
            }

            /**
             * 최소화
             */
            fun moveMax(parentView: View, view: View) {
                isPipMode = false

                binding.flBottom.alpha = 1f
                parentView.animate()
                    .translationY(0f)
                    .translationX(0f)
                    .setDuration(100)
                    .start()

                // 사이즈 조절
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }

            /**
             * 최대화
             */
            fun moveMin(parentView: View, view: View) {
                isPipMode = true
                binding.flBottom.alpha = 0f

                // 이동
                moveY = parentView.height.toFloat() - (binding.flTop.height / 2)
                moveX = parentView.width.toFloat() - (binding.flTop.width / 2)
                // 줄어든 사이즈만큼 위치 조절
                moveY -= binding.flTop.height / 4
                moveX -= binding.flTop.width / 4

                parentView.animate()
                    .translationY(moveY)
                    .translationX(moveX)
                    .setDuration(100)
                    .start()

                // 사이즈 조절
                view.animate()
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(100)
                    .start()
            }
        })
    }

    /**
     * PIP 상태에 따른 UI 변경
     */
    private fun setPipState(isPip: Boolean) {
        if (isPip) {
            binding.flController.visibility = View.GONE
        } else {
            binding.flController.visibility = View.VISIBLE
        }
    }
}
