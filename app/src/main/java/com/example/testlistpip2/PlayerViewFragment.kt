package com.example.testlistpip2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.testlistpip2.databinding.FragmentPlayerViewBinding
import com.example.testlistpip2.dragview.IDragListener

class PlayerViewFragment : Fragment(), IDragListener {

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

        binding.clPlayerLayout.init(binding.flTop, binding.flBottom, this)
        binding.btn1.setOnClickListener {
            Log.i("TEST", "CLICK 1")
        }
        binding.btn2.setOnClickListener {
            Log.i("TEST", "CLICK 2")
            onFinish()
        }
        binding.btn3.setOnClickListener {
            Log.i("TEST", "CLICK 3")
            binding.clPlayerLayout.setMinimized()
        }
    }

    override fun onFinish() {
        activity?.supportFragmentManager?.beginTransaction().let {
            it?.remove(this)
            it?.commitNowAllowingStateLoss()
        }
    }

    override fun onMaximized() {
        Log.d("TEST", "onMaximized()")
        setPipState(false)
    }

    override fun onMinimized() {
        Log.d("TEST", "onMinimized()")
        setPipState(true)
    }

    override fun onDragingStart() {
        Log.d("TEST", "onDragingStart()")
    }

    override fun onPipDragingStart() {
        Log.d("TEST", "onPipDragingStart()")
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
