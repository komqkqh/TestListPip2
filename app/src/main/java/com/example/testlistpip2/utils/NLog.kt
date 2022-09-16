package com.example.testlistpip2.utils

import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * 로그 출력용 유틸
 *
 * @author fdelpini
 */
object NLog {
    private val TAG = NLog::class.java.simpleName
    private const val DEBUG = true

    @JvmStatic
    fun w(tag: String?, format: String?, vararg args: Any?) {
        if (DEBUG) {
            Log.w(tag, currentLineInfo + String.format(format!!, *args))
        }
    }

    @JvmStatic
    fun w(tag: String?, msg: String) {
        if (DEBUG) {
            Log.w(tag, currentLineInfo + msg)
        }
    }

    @JvmStatic
    fun w(tag: String?, msg: String, tr: Throwable?) {
        if (DEBUG) {
            Log.w(tag, currentLineInfo + msg, tr)
        }
    }

    @JvmStatic
    fun w(tag: String?, tr: Throwable) {
        if (DEBUG) {
            Log.w(tag, currentLineInfo + tr)
        }
    }

    @JvmStatic
    fun i(tag: String?, format: String?, vararg args: Any?) {
        if (DEBUG) {
            Log.i(tag, currentLineInfo + String.format(format!!, *args))
        }
    }

    @JvmStatic
    fun i(tag: String?, msg: String) {
        if (DEBUG) {
            Log.i(tag, currentLineInfo + msg)
        }
    }

    @JvmStatic
    fun i(tag: String?, msg: String, tr: Throwable?) {
        if (DEBUG) {
            Log.i(tag, currentLineInfo + msg, tr)
        }
    }

    @JvmStatic
    fun v(tag: String?, format: String?, vararg args: Any?) {
        if (DEBUG) {
            Log.v(tag, currentLineInfo + String.format(format!!, *args))
        }
    }

    @JvmStatic
    fun v(tag: String?, msg: String) {
        if (DEBUG) {
            Log.v(tag, currentLineInfo + msg)
        }
    }

    @JvmStatic
    fun v(tag: String?, msg: String, tr: Throwable?) {
        if (DEBUG) {
            Log.v(tag, currentLineInfo + msg, tr)
        }
    }

    @JvmStatic
    fun d(tag: String?, format: String?, vararg args: Any?) {
        if (DEBUG) {
            Log.d(tag, currentLineInfo + String.format(format!!, *args))
        }
    }

    @JvmStatic
    fun d(tag: String?, msg: String) {
        if (DEBUG) {
            Log.d(tag, currentLineInfo + msg)
        }
    }

    @JvmStatic
    fun d(tag: String?, msg: String, tr: Throwable?) {
        if (DEBUG) {
            Log.d(tag, currentLineInfo + msg, tr)
        }
    }

    @JvmStatic
    fun e(tag: String?, format: String?, vararg args: Any?) {
        if (DEBUG) {
            Log.e(tag, currentLineInfo + String.format(format!!, *args))
        }
    }

    @JvmStatic
    fun e(tag: String?, msg: String) {
        if (DEBUG) {
            Log.e(tag, currentLineInfo + msg)
        }
    }

    @JvmStatic
    fun e(tag: String?, msg: String, tr: Throwable?) {
        if (DEBUG) {
            Log.e(tag, currentLineInfo + msg, tr)
        }
    }

    fun println(priority: Int, tag: String?, msg: String?) {
        if (DEBUG) {
            Log.println(priority, tag, msg!!)
        }
    }

    @JvmStatic
    fun showMessage(context: Context?, msg: String?) {
        if (DEBUG) {
            try {
                Toast.makeText(context, "[Debug] : $msg", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e(TAG, "Can't show debug message!\n[NLog] : $msg\nCause : ", e)
            }
        }
    }

    @JvmStatic
    fun findClass(str: String): String {
        val th = Thread.currentThread()
        val lists = th.stackTrace
        return if (lists[4].className == null) {
            ""
        } else "[" + lists[4].className + "][" + lists[4].methodName + "][" + lists[4].lineNumber + "][" + str + "]"
    }

    fun getStackTraceString(tr: Throwable?): String {
        return if (DEBUG) {
            Log.getStackTraceString(tr)
        } else ""
    } // getTag -> NLog -> 실제 호출된 클래스의 순서이므로 stack는 2를 가져온다.

    // 방어코드
    private val tag: String
        private get() {
            var result = ""
            try {
                val stacks = Throwable().stackTrace
                if (stacks.size >= 3) { // 방어코드
                    val preStack = stacks[2] // getTag -> NLog -> 실제 호출된 클래스의 순서이므로 stack는 2를 가져온다.
                    val lastIndex = preStack.className.lastIndexOf(".")
                    result = preStack.className.substring(lastIndex + 1)
                }
            } catch (e: Exception) {
                w(TAG, "[NLog][$e]", e)
            }
            return result
        }
    private val threadName: String
        private get() {
            var threadName = Thread.currentThread().name
            if (threadName.length > 30) {
                threadName = threadName.substring(0, 30) + "..."
            }
            return threadName
        }
    private val currentLineInfo: String
        private get() = try {
            val trace = Thread.currentThread().stackTrace[4]
            val strFileName = trace.fileName
            val strMethodName = trace.methodName
            val nLineNumber = trace.lineNumber
            "[" + strFileName + "][" + strMethodName + ":" + nLineNumber + "][" + threadName + "]"
        } catch (e: Exception) {
            ""
        }
}
