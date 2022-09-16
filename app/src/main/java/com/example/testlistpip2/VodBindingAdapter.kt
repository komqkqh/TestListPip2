package com.example.testlistpip2

import androidx.annotation.IdRes
import androidx.databinding.BindingAdapter
import com.example.testlistpip2.dragview.DragView

@BindingAdapter(requireAll = true, value = ["bindTopView", "bindBottomView"])
fun DragView.bindTopView(@IdRes topViewIdRes: Int, @IdRes bottomViewIdRes: Int) {
    setDragTopView(findViewById(topViewIdRes))
    setDragBottomVuew(findViewById(bottomViewIdRes))
}
