package com.catpuppyapp.puppygit.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.constants.SpecialCredential
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.utils.AppModel
import com.catpuppyapp.puppygit.utils.getFormatTimeFromSec
import com.catpuppyapp.puppygit.utils.state.CustomStateSaveable


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CredentialItem(
    showBottomSheet: MutableState<Boolean>,
    curCredentialState: CustomStateSaveable<CredentialEntity>,
    idx:Int,
    thisItem:CredentialEntity,
    isLinkMode:Boolean,
    linkedFetchId:String,
    linkedPushId:String,
    onClick:(CredentialEntity)->Unit
) {
    val haptic = AppModel.singleInstanceHolder.haptic

    val isMatchByDomain = SpecialCredential.MatchByDomain.equals_to(thisItem)
    val isNone = SpecialCredential.NONE.equals_to(thisItem)
    val isNotMatchByDomainOrNone = !(isMatchByDomain || isNone)

//    println("IDX::::::::::"+idx)
    Row(
        //0.9f 占父元素宽度的百分之90
        modifier = Modifier
            .fillMaxWidth()
//            .defaultMinSize(minHeight = 100.dp)
            .combinedClickable(
                enabled = true,
                onClick = {
                    onClick(thisItem)
                },
                onLongClick = {
                    if(isNotMatchByDomainOrNone) {
                        //震动反馈
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        curCredentialState.value = CredentialEntity()

                        //设置当前条目，供bottomsheet使用
                        curCredentialState.value = thisItem

                        //显示底部菜单
                        showBottomSheet.value = true
                    }
                },
            )
            //padding要放到 combinedClickable后面，不然点按区域也会padding
//            .background(if (idx % 2 == 0) Color.Transparent else CommitListSwitchColor)
            .padding(10.dp)

        ,

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween

    ) {
        Column(
            // .8f 给按钮留点空间，matchbydomain或none无按钮，所以占满屏幕
            modifier = if(isNotMatchByDomainOrNone) Modifier.fillMaxWidth(.8f) else Modifier.fillMaxWidth(),
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Center
        ) {

            Row (
                verticalAlignment = Alignment.CenterVertically,

                ){

                //如果关联模式，对已绑定的前面加个*
                Text(text = stringResource(R.string.name) + ":")
                Text(text = (if(isLinkMode && (linkedFetchId==thisItem.id || linkedPushId==thisItem.id)) "*" else "") + thisItem.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Light

                )
            }
//
//        Row (
//            verticalAlignment = Alignment.CenterVertically,
//
//        ){
//
//            Text(text = stringResource(R.string.type) +":")
//            Text(text = thisItem.getTypeStr(),
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis,
//                fontWeight = FontWeight.Light
//
//            )
//        }

            //给match by domain和none显示个desc，他妈的这个match by domain名字怎么这么长？该起个简单的名字，妈的
            if(isMatchByDomain || isNone) {
                Row (
                    verticalAlignment = Alignment.CenterVertically,

                    ){
                    Text(text = stringResource(R.string.desc) +":")
                    Text(text = if(isMatchByDomain) stringResource(R.string.credential_match_by_domain_note_short) else stringResource(R.string.no_credential_will_be_used),
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Light

                    )
                }
            }

            //显示编辑时间
            if(isNotMatchByDomainOrNone) {
                Row (
                    verticalAlignment = Alignment.CenterVertically,
                ){
                    Text(text = stringResource(R.string.edited) +":")
                    ScrollableRow {
                        Text(text = getFormatTimeFromSec(thisItem.baseFields.baseUpdateTime),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Light

                        )
                    }
                }
            }

        }

        //显示编辑按钮
        if(isNotMatchByDomainOrNone) {
            Column(
                modifier = Modifier.padding(end = 10.dp).size(20.dp),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.Center
            ) {
                LongPressAbleIconBtn(
                    tooltipText = stringResource(R.string.edit),
                    icon = Icons.Filled.Edit,
                    iconContentDesc = stringResource(R.string.edit),
                ) {
                    AppModel.singleInstanceHolder.navController.navigate(Cons.nav_CredentialNewOrEditScreen+"/"+thisItem.id)
                }
            }
        }
    }
}
