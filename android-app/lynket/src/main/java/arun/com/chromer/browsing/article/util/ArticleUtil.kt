/*
 * Phase 7: Converted from Java to Kotlin
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.browsing.article.util

import android.annotation.TargetApi
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.widget.EdgeEffect
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Utilities we use, mostly for UI modification.
 */
object ArticleUtil {
    /**
     * Changes the overscroll highlight effect on a recyclerview to be the given color.
     */
    fun changeRecyclerOverscrollColors(recyclerView: RecyclerView, color: Int) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var invoked = false

            @TargetApi(21)
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                // only invoke this once
                if (invoked) {
                    return
                } else {
                    invoked = true
                }

                try {
                    val clazz = RecyclerView::class.java

                    for (name in arrayOf("ensureTopGlow", "ensureBottomGlow")) {
                        val method = clazz.getDeclaredMethod(name)
                        method.isAccessible = true
                        method.invoke(recyclerView)
                    }

                    for (name in arrayOf("mTopGlow", "mBottomGlow")) {
                        val field = clazz.getDeclaredField(name)
                        field.isAccessible = true
                        val edge = field.get(recyclerView)
                        val fEdgeEffect = edge?.javaClass?.getDeclaredField("mEdgeEffect")
                        fEdgeEffect?.isAccessible = true
                        (fEdgeEffect?.get(edge) as? EdgeEffect)?.setColor(color)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    /**
     * Changes the progress bar's color.
     */
    fun changeProgressBarColors(progressBar: ProgressBar, color: Int) {
        progressBar.indeterminateDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    /**
     * Changes the text selection handle colors.
     */
    fun changeTextSelectionHandleColors(textView: TextView, color: Int) {
        textView.highlightColor = Color.argb(
            40, Color.red(color), Color.green(color), Color.blue(color)
        )

        try {
            val editorField = TextView::class.java.getDeclaredField("mEditor")
            if (!editorField.isAccessible) {
                editorField.isAccessible = true
            }

            val editor = editorField.get(textView)
            val editorClass = editor?.javaClass ?: return

            val handleNames = arrayOf(
                "mSelectHandleLeft",
                "mSelectHandleRight",
                "mSelectHandleCenter"
            )
            val resNames = arrayOf(
                "mTextSelectHandleLeftRes",
                "mTextSelectHandleRightRes",
                "mTextSelectHandleRes"
            )

            for (i in handleNames.indices) {
                val handleField = editorClass.getDeclaredField(handleNames[i])
                if (!handleField.isAccessible) {
                    handleField.isAccessible = true
                }

                var handleDrawable = handleField.get(editor) as? Drawable

                if (handleDrawable == null) {
                    val resField = TextView::class.java.getDeclaredField(resNames[i])
                    if (!resField.isAccessible) {
                        resField.isAccessible = true
                    }
                    val resId = resField.getInt(textView)
                    handleDrawable = ContextCompat.getDrawable(textView.context, resId)
                }

                handleDrawable?.let {
                    val drawable = it.mutate()
                    drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                    handleField.set(editor, drawable)
                }
            }
        } catch (ignored: Exception) {
        }
    }
}
