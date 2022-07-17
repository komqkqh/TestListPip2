package com.example.testlistpip2.dragview

/**
 * 드래그 리스너
 */
interface IDragListener {
    /**
     * pip 모드 중 종료 애니메이션 처리
     */
    fun onFinish()

    /**
     * 최대 사이즈 변환
     */
    fun onMaximized()

    /**
     * 최소 사이즈 변환
     */
    fun onMinimized()

    /**
     * 드래그 시작
     */
    fun onDragingStart()

    /**
     * pip 모드 중 드래그
     */
    fun onPipDragingStart()
}
