package com.catpuppyapp.puppygit.screen.shared

import androidx.compose.foundation.ScrollState
import com.catpuppyapp.puppygit.datastruct.Stack
import com.catpuppyapp.puppygit.screen.functions.newScrollState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class EditorPreviewNavStack(val firstPath:String) {
    private val lock = Mutex()
    private val backStack = Stack<String>()
    private val aheadStack = Stack<String>()
    private val pathScrollStateMap = ConcurrentHashMap<String, ScrollState>()

    suspend fun push(path:String) {
        lock.withLock {
            val next = backStack.getFirst()
            if(next!=null && next==path) {
                backStack.pop()
            }else {
                backStack.clear()
            }

            aheadStack.push(path)
        }
    }

    suspend fun ahead():Pair<String, ScrollState>? {
        lock.withLock {
            val nextPath = aheadStack.pop() ?: return null;

            backStack.push(nextPath)

            return Pair(nextPath, getScrollState(nextPath))
        }
    }

    suspend fun back():Pair<String, ScrollState>? {
        lock.withLock {
            val lastPath = backStack.pop() ?: return null;

            aheadStack.push(lastPath)

            return Pair(lastPath, getScrollState(lastPath))
        }
    }

    suspend fun getFirst(trueAheadFalseBack:Boolean):Pair<String, ScrollState>? {
        lock.withLock {
            val first = (if(trueAheadFalseBack) aheadStack else backStack).getFirst() ?: return null
            return Pair(first, getScrollState(first))
        }
    }


    fun getScrollState(path:String):ScrollState {
        return pathScrollStateMap[path] ?: run {
            val scrollState = newScrollState()
            pathScrollStateMap[path] = scrollState
            scrollState
        }
    }


}
