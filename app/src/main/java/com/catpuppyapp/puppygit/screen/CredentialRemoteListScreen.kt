package com.catpuppyapp.puppygit.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.compose.ConfirmDialog
import com.catpuppyapp.puppygit.compose.CopyableDialog
import com.catpuppyapp.puppygit.compose.FilterTextField
import com.catpuppyapp.puppygit.compose.GoToTopAndGoToBottomFab
import com.catpuppyapp.puppygit.compose.InfoDialog
import com.catpuppyapp.puppygit.compose.LinkOrUnLinkCredentialAndRemoteDialog
import com.catpuppyapp.puppygit.compose.LongPressAbleIconBtn
import com.catpuppyapp.puppygit.compose.MyLazyColumn
import com.catpuppyapp.puppygit.compose.RemoteItemForCredential
import com.catpuppyapp.puppygit.compose.ScrollableColumn
import com.catpuppyapp.puppygit.compose.ScrollableRow
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.constants.SpecialCredential
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.dto.RemoteDtoForCredential
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.screen.functions.defaultTitleDoubleClick
import com.catpuppyapp.puppygit.screen.functions.filterModeActuallyEnabled
import com.catpuppyapp.puppygit.screen.functions.filterTheList
import com.catpuppyapp.puppygit.screen.functions.triggerReFilter
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.style.MyStyleKt
import com.catpuppyapp.puppygit.utils.AppModel
import com.catpuppyapp.puppygit.utils.Msg
import com.catpuppyapp.puppygit.utils.MyLog
import com.catpuppyapp.puppygit.utils.changeStateTriggerRefreshPage
import com.catpuppyapp.puppygit.utils.createAndInsertError
import com.catpuppyapp.puppygit.utils.doJobThenOffLoading
import com.catpuppyapp.puppygit.utils.state.mutableCustomStateListOf
import com.catpuppyapp.puppygit.utils.state.mutableCustomStateOf

private const val TAG = "CredentialRemoteListScreen"
private const val stateKeyTag = "CredentialRemoteListScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CredentialRemoteListScreen(
    credentialId:String,
    isShowLink:Boolean,
    naviUp: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val homeTopBarScrollBehavior = AppModel.homeTopBarScrollBehavior
    val navController = AppModel.navController
    val activityContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsUtil.getSettingsSnapshot() }

    val isNonePage = credentialId == SpecialCredential.NONE.credentialId

    //这个页面的滚动状态不用记住，每次点开重置也无所谓
    val listState = rememberLazyListState()
    //如果再多几个"mode"，就改用字符串判断，直接把mode含义写成常量
//    val isSearchingMode = rememberSaveable { mutableStateOf(false)}
//    val isShowSearchResultMode = rememberSaveable { mutableStateOf(false)}
//    val searchKeyword = rememberSaveable { mutableStateOf("")}
//    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = MyStyleKt.BottomSheet.skipPartiallyExpanded)
//    val showBottomSheet = rememberSaveable { mutableStateOf(false)}
    val showUnLinkAllDialog = rememberSaveable { mutableStateOf(false)}
//    val curCommit = rememberSaveable{ mutableStateOf(CommitDto()) }
    val curItemInPage = mutableCustomStateOf(keyTag = stateKeyTag, keyName = "curItemInPage", initValue = CredentialEntity())
    val list = mutableCustomStateListOf(keyTag = stateKeyTag, keyName = "list", initValue = listOf<RemoteDtoForCredential>())
    val filterList = mutableCustomStateListOf(keyTag = stateKeyTag, keyName = "filterList", initValue = listOf<RemoteDtoForCredential>())
    val needOverrideLinkItem = mutableCustomStateOf(keyTag = stateKeyTag, keyName = "needOverrideLinkItem", initValue = RemoteDtoForCredential())
    val showOverrideLinkDialog = rememberSaveable { mutableStateOf(false)}
    val needRefresh = rememberSaveable { mutableStateOf("")}

    val showLinkOrUnLinkDialog = rememberSaveable { mutableStateOf( false)}
    val requireDoLink = rememberSaveable { mutableStateOf(false)}
    val targetAll = rememberSaveable { mutableStateOf(false)}
    val curItem = mutableCustomStateOf(keyTag = stateKeyTag, keyName = "curItem", initValue = RemoteDtoForCredential())
    val linkOrUnlinkDialogTitle = rememberSaveable { mutableStateOf("") }


    val doLink= { remoteId: String ->
        doJobThenOffLoading {
            val remoteDb = AppModel.dbContainer.remoteRepository
            remoteDb.linkCredentialIdByRemoteId(remoteId, curItemInPage.value.id)
            changeStateTriggerRefreshPage(needRefresh)

        }
    }
//    val doUnLink={ remoteId:String ->
//        doJobThenOffLoading {
//            val remoteDb = AppModel.dbContainer.remoteRepository
//            remoteDb.unlinkCredentialIdByRemoteId(remoteId)
//            changeStateTriggerRefreshPage(needRefresh)
//        }
//    }

    val doUnLinkAll = {
        //确认后执行此方法
        doJobThenOffLoading {
            val remoteDb = AppModel.dbContainer.remoteRepository
            remoteDb.unlinkAllCredentialIdByCredentialId(curItemInPage.value.id)
            changeStateTriggerRefreshPage(needRefresh)
        }
    }

    if(showUnLinkAllDialog.value) {
        ConfirmDialog(title = stringResource(id = R.string.unlink_all),
            text = stringResource(id = R.string.unlink_all_ask_text),
            okTextColor = MyStyleKt.TextColor.danger(),
            onCancel = {showUnLinkAllDialog.value=false }
        ) {
            showUnLinkAllDialog.value=false
            doUnLinkAll()
        }
    }

    if(showOverrideLinkDialog.value) {
        ConfirmDialog(title = stringResource(id = R.string.override_link),
            text = stringResource(id = R.string.override_link_ask_text),
            okTextColor = MyStyleKt.TextColor.danger(),
            onCancel = {showOverrideLinkDialog.value=false }
        ) {
            showOverrideLinkDialog.value=false
            doLink(needOverrideLinkItem.value.remoteId)
        }
    }

    if(showLinkOrUnLinkDialog.value) {
        LinkOrUnLinkCredentialAndRemoteDialog(
            curItemInPage,
            requireDoLink.value,
            targetAll.value,
            linkOrUnlinkDialogTitle.value,
            curItem.value,
            onCancel = {showLinkOrUnLinkDialog.value=false},
            onFinallyCallback = {
                showLinkOrUnLinkDialog.value=false
                changeStateTriggerRefreshPage(needRefresh)
            },
            onErrCallback = { e->
                //不用写仓库名，因为错误会归类到对应仓库id上，所以，会在对应仓库卡片上看到错误信息，因此点记错误时可以通过从哪点进来的得知是哪个仓库的错误信息，再通过错误信息更进一步知道remote关联哪个credential发生的错误
                val errMsgPrefix = "${linkOrUnlinkDialogTitle.value} err: remote='${curItem.value.remoteName}', credential=${curItemInPage.value.name}, err="
                Msg.requireShowLongDuration(e.localizedMessage ?: errMsgPrefix)
                createAndInsertError(curItem.value.repoId, errMsgPrefix + e.localizedMessage)
                MyLog.e(TAG, "#LinkOrUnLinkCredentialAndRemoteDialog err: $errMsgPrefix${e.stackTraceToString()}")
            },
            onOkCallback = {
                Msg.requireShow(activityContext.getString(R.string.success))
            }
        )
    }


    //filter相关，开始
    val filterResultNeedRefresh = rememberSaveable { mutableStateOf("") }
    val filterKeyword = mutableCustomStateOf(
        keyTag = stateKeyTag,
        keyName = "filterKeyword",
        initValue = TextFieldValue("")
    )
    val filterModeOn = rememberSaveable { mutableStateOf(false)}
    //filter相关，结束

    // start: search states
    val lastKeyword = rememberSaveable { mutableStateOf("") }
    val token = rememberSaveable { mutableStateOf("") }
    val searching = rememberSaveable { mutableStateOf(false) }
    val resetSearchVars = {
        searching.value = false
        token.value = ""
        lastKeyword.value = ""
    }
    // end: search states


    // 向下滚动监听，开始
    val pageScrolled = rememberSaveable { mutableStateOf(settings.showNaviButtons) }

    val filterListState = rememberLazyListState()
//    val filterListState = mutableCustomStateOf(
//        keyTag = stateKeyTag,
//        keyName = "filterListState",
//        LazyListState(0,0)
//    )
    val enableFilterState = rememberSaveable { mutableStateOf(false)}
//    val firstVisible = remember { derivedStateOf { if(enableFilterState.value) filterListState.value.firstVisibleItemIndex else listState.firstVisibleItemIndex } }
//    ScrollListener(
//        nowAt = firstVisible.value,
//        onScrollUp = {scrollingDown.value = false}
//    ) { // onScrollDown
//        scrollingDown.value = true
//    }
//
//    val lastAt = remember { mutableIntStateOf(0) }
//    val lastIsScrollDown = remember { mutableStateOf(false) }
//    val forUpdateScrollState = remember {
//        derivedStateOf {
//            val nowAt = if(enableFilterState.value) {
//                filterListState.firstVisibleItemIndex
//            } else {
//                listState.firstVisibleItemIndex
//            }
//            val scrolledDown = nowAt > lastAt.intValue  // scroll down
////            val scrolledUp = nowAt < lastAt.intValue
//
//            val scrolled = nowAt != lastAt.intValue  // scrolled
//            lastAt.intValue = nowAt
//
//            // only update state when this scroll down and last is not scroll down, or this is scroll up and last is not scroll up
//            if(scrolled && ((lastIsScrollDown.value && !scrolledDown) || (!lastIsScrollDown.value && scrolledDown))) {
//                pageScrolled.value = true
//            }
//
//            lastIsScrollDown.value = scrolledDown
//        }
//    }.value
    // 向下滚动监听，结束


    val titleString = rememberSaveable { mutableStateOf("")}
    val titleSecondaryString = rememberSaveable { mutableStateOf("")}  // title secondary line string
    val showTitleInfoDialog = rememberSaveable { mutableStateOf(false)}
    if(showTitleInfoDialog.value) {
        InfoDialog(showTitleInfoDialog) {
            ScrollableColumn {
                Text(titleString.value)

                if(titleSecondaryString.value.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(titleSecondaryString.value)
                }
            }
        }
    }

    val urlForShow = rememberSaveable { mutableStateOf("")}
    val titleForShow = rememberSaveable { mutableStateOf("")}
    val showUrlDialogState = rememberSaveable { mutableStateOf(false)}
    val showUrlDialog = { title:String, url:String ->
        titleForShow.value = title
        urlForShow.value = url
        showUrlDialogState.value = true
    }
    if(showUrlDialogState.value) {
        CopyableDialog(
            title = titleForShow.value,
            text = urlForShow.value,
            onCancel = {showUrlDialogState.value=false},
        ) {
            //复制到剪贴板
            showUrlDialogState.value=false
            clipboardManager.setText(AnnotatedString(urlForShow.value))
            Msg.requireShow(activityContext.getString(R.string.copied))
        }
    }

    val filterLastPosition = rememberSaveable { mutableStateOf(0) }
    val lastPosition = rememberSaveable { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.nestedScroll(homeTopBarScrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    if(filterModeOn.value) {
                        FilterTextField(filterKeyWord = filterKeyword, loading = searching.value)
                    }else{
                        Column (modifier = Modifier.combinedClickable(onDoubleClick = {
                            defaultTitleDoubleClick(scope, listState, lastPosition)
                        }) {
                            showTitleInfoDialog.value = true
                        }){
                            ScrollableRow {
                                titleString.value = if(isShowLink) stringResource(R.string.linked_remotes) else stringResource(R.string.unlinked_remotes)
                                Text(
                                    text= titleString.value,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                            }
                            ScrollableRow {
                                titleSecondaryString.value = "["+curItemInPage.value.name+"]"
                                Text(
                                    text= titleSecondaryString.value,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = MyStyleKt.Title.secondLineFontSize
                                )

                            }

                        }

                    }

                },
                navigationIcon = {
                    if(filterModeOn.value) {
                        LongPressAbleIconBtn(
                            tooltipText = stringResource(R.string.close),
                            icon = Icons.Filled.Close,
                            iconContentDesc = stringResource(R.string.close),

                        ) {
                            resetSearchVars()
                            filterModeOn.value = false
                        }
                    }else{
                        LongPressAbleIconBtn(
                            tooltipText = stringResource(R.string.back),
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            iconContentDesc = stringResource(R.string.back),

                        ) {
                            naviUp()
                        }

                    }
                },
                actions = {
                    if(!filterModeOn.value) {
                        Row {
                            LongPressAbleIconBtn(
                                tooltipText = stringResource(R.string.filter),
                                icon =  Icons.Filled.FilterAlt,
                                iconContentDesc = stringResource(R.string.filter),
                            ) {
                                // filter item
                                filterKeyword.value = TextFieldValue("")
                                filterModeOn.value = true
                            }

                            LongPressAbleIconBtn(
                                tooltipText = stringResource(R.string.refresh),
                                icon =  Icons.Filled.Refresh,
                                iconContentDesc = stringResource(id = R.string.refresh),
                            ) {
                                changeStateTriggerRefreshPage(needRefresh)
                            }

                            if (isShowLink) {
                                LongPressAbleIconBtn(
                                    tooltipText = stringResource(R.string.unlink_all),
                                    icon = Icons.Filled.LinkOff,
                                    iconContentDesc = stringResource(R.string.unlink_all),
                                    enabled = list.value.isNotEmpty()
                                ) {
//                                showUnLinkAllDialog.value = true

                                    requireDoLink.value = false
                                    targetAll.value = true
                                    linkOrUnlinkDialogTitle.value = activityContext.getString(R.string.unlink_all)
                                    showLinkOrUnLinkDialog.value = true
                                }

                                LongPressAbleIconBtn(
                                    tooltipText = stringResource(R.string.create_link),  //新建关联（显示未关联列表）
                                    icon = Icons.Filled.AddLink,
                                    iconContentDesc = stringResource(R.string.create_link),

                                    ) {
                                    navController.navigate(Cons.nav_CredentialRemoteListScreen + "/" + credentialId + "/0")
                                }

                            }
                        }

                    }

                },
                scrollBehavior = homeTopBarScrollBehavior,
            )
        },
        floatingActionButton = {
            if(pageScrolled.value) {

                GoToTopAndGoToBottomFab(
                    filterModeOn = enableFilterState.value,
                    scope = scope,
                    filterListState = filterListState,
                    listState = listState,
                    filterListLastPosition = filterLastPosition,
                    listLastPosition = lastPosition,
                    showFab = pageScrolled
                )

            }
        }
    ) { contentPadding ->

        //根据关键字过滤条目
        val keyword = filterKeyword.value.text.lowercase()  //关键字
        val enableFilter = filterModeActuallyEnabled(filterModeOn.value, keyword)

        val lastNeedRefresh = rememberSaveable { mutableStateOf("") }
        val list = filterTheList(
            needRefresh = filterResultNeedRefresh.value,
            lastNeedRefresh = lastNeedRefresh,
            enableFilter = enableFilter,
            keyword = keyword,
            lastKeyword = lastKeyword,
            searching = searching,
            token = token,
            activityContext = activityContext,
            filterList = filterList.value,
            list = list.value,
            resetSearchVars = resetSearchVars,
            match = { idx:Int, it: RemoteDtoForCredential ->
                it.repoName.lowercase().contains(keyword)
                        || it.remoteName.lowercase().contains(keyword)
                        || it.remoteFetchUrl.lowercase().contains(keyword)
                        || it.remotePushUrl.lowercase().contains(keyword)
                        || it.getCredentialNameOrNone(activityContext).lowercase().contains(keyword)
                        || it.getPushCredentialNameOrNone(activityContext).lowercase().contains(keyword)

            }
        )

        val listState = if(enableFilter) filterListState else listState
//        if(enableFilter) {  //更新filter列表state
//            filterListState.value = listState
//        }
        //更新是否启用filter
        enableFilterState.value = enableFilter

        MyLazyColumn(
            contentPadding = contentPadding,
            list = list,
            listState = listState,
            requireForEachWithIndex = true,
            requirePaddingAtBottom = true
        ) { idx,it->
            RemoteItemForCredential(
                isShowLink=isShowLink,
                idx = idx, thisItem = it,
                showUrlDialog = showUrlDialog,
                actText = if(isShowLink) stringResource(R.string.unlink) else stringResource(R.string.link),

                //如果是 None页面 且 是关联模式 且 条目fetch和push凭据id都为空，则不需要显示unlink，因为在无凭据条目列表将条目unlink到无凭据没有意义，执行了也没效果
                actAction = if(isNonePage && isShowLink && it.credentialId.isNullOrEmpty() && it.pushCredentialId.isNullOrEmpty()) null else ({
                    curItem.value = it
                    requireDoLink.value = !isShowLink
                    targetAll.value = false
                    linkOrUnlinkDialogTitle.value=if(requireDoLink.value) activityContext.getString(R.string.link) else activityContext.getString(R.string.unlink)  // (不建议，不方便记Err)若空字符串，将会自动根据requireDoLink的值决定使用link还是unlink作为title
                    showLinkOrUnLinkDialog.value=true

//                if(isShowLink) {  //如果是显示已关联条目的页面，点击取关直接执行
//                    doUnLink(it.remoteId)
//                }else{  //如果是显示未关联条目的页面，检查是否已关联其他凭据，如果没有，直接关联，如果关联了，询问是否覆盖
//                    if(it.credentialName==null || it.credentialName!!.isBlank()) {  //没关联其他凭据
//                        doLink(it.remoteId)
//                    }else { //条目已经关联了其他credential，弹窗询问是否覆盖
//                        needOverrideLinkItem.value = it
//                        showOverrideLinkDialog.value = true
//                    }
//                }

                })
            )

            HorizontalDivider()

        }

    }

    BackHandler {
        if(filterModeOn.value) {
            filterModeOn.value = false
        } else {
            naviUp()
        }
    }

    //compose创建时的副作用
    LaunchedEffect(needRefresh.value) {
        try {
            doJobThenOffLoading {
                val remoteDb = AppModel.dbContainer.remoteRepository
                val credentialDb = AppModel.dbContainer.credentialRepository
                //这个页面用不到密码，所以查询的是加密后的密码，没解密
                curItemInPage.value = credentialDb.getById(credentialId, includeNone = true, includeMatchByDomain = true) ?: CredentialEntity(id="")
                val listFromDb = if (isShowLink) {
                    remoteDb.getLinkedRemoteDtoForCredentialList(credentialId)
                }else {
                    remoteDb.getUnlinkedRemoteDtoForCredentialList(credentialId)
                }
                list.value.clear()
                list.value.addAll(listFromDb)

                triggerReFilter(filterResultNeedRefresh)
//                list.requireRefreshView()
            }
        } catch (cancel: Exception) {
//            println("LaunchedEffect: job cancelled")
        }
    }

}
