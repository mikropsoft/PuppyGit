package com.catpuppyapp.puppygit.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.dto.RemoteDtoForCredential
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.style.MyStyleKt


@Composable
fun RemoteItemForCredential(
    isShowLink:Boolean,
    idx:Int,
    thisItem:RemoteDtoForCredential,
    actText:String,
    showUrlDialog:(title:String, url:String) -> Unit,
    actAction:(()->Unit)?,
) {
    val activityContext = LocalContext.current

    Row(
        //0.9f 占父元素宽度的百分之90
        modifier = Modifier
            .fillMaxWidth()
//            .defaultMinSize(minHeight = 100.dp)
            //padding要放到 combinedClickable后面，不然点按区域也会padding
//            .background(if (idx % 2 == 0) Color.Transparent else CommitListSwitchColor)
            .padding(10.dp)
        ,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        // .8f width, make sure right content still stay in screen when left content very long
        // .8f 宽度确保当左边内容很长时右边内容不会被顶出屏幕
        //如果不需要执行操作，右边没元素，填满全部宽度，否则填8成
        Column(modifier = if(actAction == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(.8f)) {
            Row (
                verticalAlignment = Alignment.CenterVertically,
            ){

                Text(text = stringResource(R.string.repo) +": ")
                Text(text = thisItem.repoName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Light
                )
            }

            Row (
                verticalAlignment = Alignment.CenterVertically,
            ){
                Text(text = stringResource(R.string.remote) +": ")
                Text(text = thisItem.remoteName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Light

                )
            }

            val fetchUrlTitle = stringResource(R.string.fetch_url)
            Row (
                verticalAlignment = Alignment.CenterVertically,
            ){
                Text(text = fetchUrlTitle+": ")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                        .clickable {
                           showUrlDialog(fetchUrlTitle, thisItem.remoteFetchUrl)
                        }
                ) {
                    Text(text = thisItem.remoteFetchUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            val pushUrlTitle = stringResource(R.string.push_url)
            Row (
                verticalAlignment = Alignment.CenterVertically,
            ){
                Text(text = pushUrlTitle+": ")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                        .clickable {
                           showUrlDialog(pushUrlTitle, thisItem.remotePushUrl)
                        }
                ){
                    Text(text = thisItem.remotePushUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            Row (
                verticalAlignment = Alignment.CenterVertically,
            ){
                Text(text = stringResource(R.string.fetch_linked) +": ")
                Text(text = thisItem.getCredentialNameOrNone(activityContext),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Light
                )
            }

            Row (
                verticalAlignment = Alignment.CenterVertically,
            ){
                Text(text = stringResource(R.string.push_linked) +": ")
                Text(text = thisItem.getPushCredentialNameOrNone(activityContext),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Light
                )
            }

        }

        if(actAction != null){
            Column {
                ClickableText (
                    text = actText,
                    modifier = MyStyleKt.ClickableText.modifier.clickable(onClick = {
                        actAction()
                    }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}
