package com.catpuppyapp.puppygit.compose


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.style.MyStyleKt
import com.catpuppyapp.puppygit.utils.Msg
import com.catpuppyapp.puppygit.utils.doJobThenOffLoading


@Composable
fun <T> SelectedItemDialog(
    title: String = stringResource(R.string.selected_str),
    selectedItems:List<T>,
    formatter:(T)->String,
    switchItemSelected:(T)->Unit,  //用switch而不是单纯的remove是为了日后可实现撤销删除方便，只要再把条目传给switchItemSelected函数，就能重新选中条目，但这样的话，恢复后的条目会在列表末尾，若想回到原位呢？难道删除后做整个列表的备份？或者这个函数改成能指定索引的？

    //清空条目列表
    clearAll:()->Unit,

    //此函数应该仅关闭弹窗，不要执行多余操作
    closeDialog:()->Unit,

    // cancel可能包含比closeDialog更多的操作
    onCancel:()->Unit = closeDialog
) {
    SelectedItemDialog3(
        title = title,
        selectedItems = selectedItems,
        text = { Text(text = formatter(it)) },
        textFormatterForCopy = formatter,
        switchItemSelected = switchItemSelected,
        clearAll = clearAll,
        closeDialog = closeDialog,
        onCancel = onCancel,

    )
}

/**
 * 相比第一版，提供更多自定义的可能性，可用 Compose组件自定义文本和尾部图标
 */
@Composable
fun <T> SelectedItemDialog2(
    selectedItems:List<T>,
    title:String,
    text:@Composable BoxScope.(T) -> Unit,
    trailIcon:@Composable BoxScope.(T) -> Unit,
    clearAll:()->Unit,
    closeDialog:()->Unit,
    onCancel:()->Unit,
    onCopy:()->Unit
) {
    CopyableDialog2(
        title = title,
//            text = selectedItemsShortDetailsStr.value,
        requireShowTextCompose = true,
        textCompose = {
            ScrollableColumn {
                selectedItems.forEach {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        text(it)

                        trailIcon(it)
                    }

                    HorizontalDivider()
                }

            }
        },
        cancelCompose = {
            ScrollableRow {
                //清空按钮
                if(selectedItems.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            closeDialog()
                            clearAll()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.clear),
                            color = MyStyleKt.TextColor.danger(),
                        )
                    }
                }

                //关闭按钮
                TextButton(
                    onClick = onCancel
                ) {
                    Text(
                        text = stringResource(R.string.close),
                        color = Color.Unspecified,
                    )
                }
            }
        },

        //就算上面自定义了cancel样式，也得传onCancel，因为onDismiss要用
        onCancel = onCancel,

        //仅有条目时才可复制
        okBtnEnabled = selectedItems.isNotEmpty(),
    ) {  //点击拷贝按钮的回调
        onCopy()
    }
}



/**
 * SelectedItemDialog2的简化版，SelectedItemDialog的增强版
 */
@Composable
fun <T> SelectedItemDialog3(
    title: String = stringResource(R.string.selected_str),
    selectedItems:List<T>,
    switchItemSelected:(T)->Unit,  //用switch而不是单纯的remove是为了日后可实现撤销删除方便，只要再把条目传给switchItemSelected函数，就能重新选中条目，但这样的话，恢复后的条目会在列表末尾，若想回到原位呢？难道删除后做整个列表的备份？或者这个函数改成能指定索引的？

    //自定义text
    text:@Composable RowScope.(T) -> Unit,
    textContainerModifier: BoxScope.() -> Modifier = { Modifier.fillMaxWidth(.8f).padding(start = 5.dp).align(Alignment.CenterStart) },
    textFormatterForCopy:(T)->String,

    //清空条目列表
    clearAll:()->Unit,

    //此函数应该仅关闭弹窗，不要执行多余操作
    closeDialog:()->Unit,

    // cancel可能包含比closeDialog更多的操作
    onCancel:()->Unit = closeDialog
) {
    val clipboardManager = LocalClipboardManager.current
    val activityContext = LocalContext.current

    SelectedItemDialog2(
        selectedItems = selectedItems,
        title = title,
        text = {
            Row(
                modifier = textContainerModifier(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                text(it)
            }
        },
        trailIcon = {
            IconButton(
                modifier = Modifier.fillMaxWidth(.2f).align(Alignment.CenterEnd),
                onClick = { switchItemSelected(it) }
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.trash_bin_icon_for_delete_item)
                )
            }
        },
        clearAll = clearAll,
        closeDialog = closeDialog,
        onCancel = onCancel,
        onCopy = {
            closeDialog()

            doJobThenOffLoading {
                val lb = Cons.lineBreak
                val sb = StringBuilder()
                selectedItems.forEach {
                    sb.append(textFormatterForCopy(it)).append(lb)
                }

                clipboardManager.setText(AnnotatedString(sb.removeSuffix(lb).toString()))
                Msg.requireShow(activityContext.getString(R.string.copied))
            }
        }
    )
}
