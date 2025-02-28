package com.catpuppyapp.puppygit.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.constants.LineNum
import com.catpuppyapp.puppygit.dto.FileSimpleDto
import com.catpuppyapp.puppygit.etc.Ret
import com.catpuppyapp.puppygit.fileeditor.texteditor.state.TextEditorState
import com.catpuppyapp.puppygit.play.pro.R
import com.catpuppyapp.puppygit.settings.AppSettings
import com.catpuppyapp.puppygit.utils.snapshot.SnapshotFileFlag
import com.catpuppyapp.puppygit.utils.snapshot.SnapshotUtil
import com.catpuppyapp.puppygit.utils.state.CustomStateSaveable
import com.catpuppyapp.puppygit.utils.temp.TempFileFlag
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale

private const val TAG = "FsUtils"

object FsUtils {

    const val rootPath = "/"
    /**
     * internal and external storage path prefix
     */
    const val internalPathPrefix = "Internal:/"
    const val externalPathPrefix = "External:/"

    //必须和 AndroidManifest.xml 里的 provider.android:authorities 的值一样
//    const val PROVIDER_AUTHORITY = "com.catpuppyapp.puppygit.play.pro.fileprovider"

    const val textMIME = "text/plain"
    const val appExportFolderName = "PuppyGitExport"
    const val appExportFolderNameUnderDocumentsDirShowToUser = "Documents/${appExportFolderName}"  //显示给用户看的路径

    enum class CopyFileConflictStrategy(val code:Int) {
        /**
         * skip exists files
         */
        SKIP(1),

        /**
         * rename exists files
         */
        RENAME(2),

        /**
         * clear folder if exists, overwrite file if exists
         */
        OVERWRITE_FOLDER_AND_FILE(3),
        ;

        companion object {
            fun fromCode(code: Int): CopyFileConflictStrategy? {
                return CopyFileConflictStrategy.entries.find { it.code == code }
            }
        }
    }

    object Patch {
        const val suffix = ".patch"

        fun getPatchDir():File{
            return AppModel.getOrCreatePatchDir()
        }

        fun newPatchFile(repoName:String, commitLeft:String, commitRight:String):File {
            val patchDir = getPatchDir()

            //在patchdir创建repo目录 (patch目录结构：patchDir/repoName/xxx..xxx.patch)
            val parentDir = File(patchDir, repoName)
            if(!parentDir.exists()) {
                parentDir.mkdirs()
            }

            val commitLeft = Libgit2Helper.getShortOidStrByFull(commitLeft)
            val commitRight = Libgit2Helper.getShortOidStrByFull(commitRight)

            var file = File(parentDir.canonicalPath, genFileName(commitLeft, commitRight))
            if(file.exists()) {  //如果文件已存在，重新生成一个，当然，仍然有可能存在，不过概率非常非常非常小，可忽略不计，因为文件名包含随机数和精确到分钟的时间戳
                file = File(parentDir.canonicalPath, genFileName(commitLeft, commitRight))
                if(file.exists()) {
                    file = File(parentDir.canonicalPath, genFileName(commitLeft, commitRight))
                    if(file.exists()) {
                        file = File(parentDir.canonicalPath, genFileName(commitLeft, commitRight))
                    }
                }
            }

            return file
        }

        private fun genFileName(commitLeft: String, commitRight: String):String {
            //文件名示例：abc1234..def3456-adeq12-202405031122.patch
            return "$commitLeft..$commitRight-${getShortUUID(6)}-${getNowInSecFormatted(Cons.dateTimeFormatter_yyyyMMddHHmm)}$suffix"
        }
    }

    object FileMimeTypes {
        val typeList= listOf(
            "text/plain",
            "image/*",
            "audio/*",
            "video/*",
            "application/zip",  //暂时用zip代替归档文件(压缩文件)，因为压缩mime类型有好多个！用模糊的application/*支持的程序不多，只有zip支持的最多！而且解压程序一般会根据二进制内容判断具体类型，所以，用zip实际上效果不错
            "*/*",
        )

        fun getTextList(activityContext:Context) :List<String> {
            return listOf(
                activityContext.getString(R.string.file_open_as_type_text),
                activityContext.getString(R.string.file_open_as_type_image),
                activityContext.getString(R.string.file_open_as_type_audio),
                activityContext.getString(R.string.file_open_as_type_video),
                activityContext.getString(R.string.file_open_as_type_archive),
                activityContext.getString(R.string.file_open_as_type_any),
            )
        }

    }

    private fun getMimeType(url: String): String {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(
            url
                .lowercase(Locale.getDefault())
        )
        if (extension != null) {
            val mime = MimeTypeMap.getSingleton()
            type = mime.getMimeTypeFromExtension(extension)
        }

        //TODO 改成如果文件类型未知，返回null，然后列出几种类型，让用户选则以什么类型打开（文本、图像、视频，还有啥来着？找个文件管理器app的打开方式看下）
        //如果文件类型未知，当作文本文件
        if (type == null) {
            type = textMIME
        }
        return type
    }

    @Deprecated("instead by MimeType#guessXXX serials function")
    fun getMimeTypeForFilePath(context: Context, fullPathOfFile:String): String {
        val file = File(fullPathOfFile)
        return getMimeType(getUriForFile(context, file).toString())
    }

    /**
     * get authority for gen uri for file
     * note: the value must same as provider.android:authorities in AndroidManifest.xml
     */
    fun getAuthorityOfUri():String {
        return AppModel.appPackageName + ".provider"
    }

    fun getUriForFile(context: Context, file: File):Uri {
        val uri = FileProvider.getUriForFile(
            context,
            getAuthorityOfUri(),
            file
        )

        MyLog.d(TAG, "#getUriForFile: uri='$uri'")

        return uri
    }

    /**
     * This function origin version(by sheimi maybe) from: https://github.com/maks/MGit/blob/66ec88b8a9873ba3334d2b6b213801a9e8d9d3c7/app/src/main/java/me/sheimi/android/utils/FsUtils.java#L119C24-L119C32
     */
    fun openFileEditFirstIfFailedThenTryView(context: Context, file: File): Ret<String?> {
        val uri = getUriForFile(context, file)
        val mimeType = getMimeType(uri.toString())
        val intent = Intent(Intent.ACTION_EDIT)  //先尝试用编辑模式打开
        intent.setDataAndType(uri, mimeType)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return Ret.createSuccess(null, "success open file with 'EDIT' mode", Ret.SuccessCode.openFileWithEditMode)
        } catch (e: Exception) {
            MyLog.e(TAG, "#openFileEditFirstIfFailedThenTryView(): try open file(path=${file.canonicalPath}) with 'EDIT' mode err, will try open with 'VIEW' mode.\n" + e.stackTraceToString())

            //If no app can edit this file at least try to view it (PDFs, ...)
            intent.setAction(Intent.ACTION_VIEW)  //如果编辑模式失败，尝试用预览方式打开
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return Ret.createSuccess(null, "success open file with 'VIEW' mode", Ret.SuccessCode.openFileWithViewMode)
            } catch (e1: Exception) {
                MyLog.e(TAG, "#openFileEditFirstIfFailedThenTryView(): open file(path=${file.canonicalPath}) with 'VIEW' mode err, give up.\n" + e1.stackTraceToString())
                return Ret.createError(null, "open file failed", Ret.ErrCode.openFileFailed)
            }
        }
    }

    fun openFile(context: Context, file: File, mimeType: String, readOnly:Boolean):Boolean {
        try {
            val uri = getUriForFile(context, file)

    //        val intent = if(readOnly) Intent(Intent.ACTION_VIEW) else Intent(Intent.ACTION_EDIT)
            val intent = Intent(if(readOnly) Intent.ACTION_VIEW else Intent.ACTION_EDIT)
            intent.setDataAndType(uri, mimeType)
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            //如果非read only，追加写权限
            if(!readOnly) {
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            MyLog.e(TAG, "#openFile(): try open file(path=${file.canonicalPath}) err! params is: mimeType=$mimeType, readOnly=$readOnly\n" + e.stackTraceToString())
            return false
        }
    }

    @Deprecated("instead by FsUtils#openFile")
    fun openFileAsEditMode(context: Context, file: File):Boolean {
        val uri = getUriForFile(context, file)

        val mimeType = getMimeType(uri.toString())
        val intent = Intent(Intent.ACTION_EDIT)  //用编辑模式打开
        intent.setDataAndType(uri, mimeType)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            MyLog.e(TAG, "#openFileAsEditMode(): try open file(path=${file.canonicalPath}) with 'EDIT' mode err!\n" + e.stackTraceToString())
            return false
        }
    }

    @Deprecated("instead by FsUtils#openFile")
    fun openFileAsViewMode(context: Context, file: File):Boolean {
        val uri = getUriForFile(context, file)

        val mimeType = getMimeType(uri.toString())
        val intent = Intent(Intent.ACTION_VIEW)  //预览模式(只读)
        intent.setDataAndType(uri, mimeType)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            MyLog.e(TAG, "#openFileAsViewMode(): try open file(path=${file.canonicalPath}) with 'VIEW' mode err!\n" + e.stackTraceToString())
            return false
        }
    }

    fun getExportDirUnderPublicDocument():Ret<File?> {
        return createDirUnderPublicExternalDir(dirNameWillCreate=appExportFolderName, publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
    }

    private fun createDirUnderPublicExternalDir(dirNameWillCreate: String, publicDir:File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)): Ret<File?> {

        //经过我的测试，api 26，安卓8.0.0 并不能访问公开目录(Documents/Pictures)之类的，所以这个判断没什么卵用，就算通过了，也不一定能获取到公开目录，20240424改用saf导出文件了，saf从安卓4.0(ndk19)开始支持，兼容性更好
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {  //需要用到安卓8新增的 getExternalStoragePublicDirectory() api
            return Ret.createError(null, "Doesn't support export file to public dir on android version lower than 8", Ret.ErrCode.doesntSupportAndroidVersion)
        }

        //注：getExternalStoragePublicDirectory() 这个api在8添加，在29弃用(deprecated)了，弃用了但没删除，所以应该也能用
        val dir:File = File(publicDir.canonicalPath, dirNameWillCreate)

//        else {  //没测试，getExternalStorageDirectory() 应该需要权限
//            File(Environment.getExternalStorageDirectory().toString() + "/" + FolderName)
//        }

        // Make sure the path directory exists.
        if(dir!=null) {
            if(!dir.exists()) {
                // Make it, if it doesn't exit
                val success = dir.mkdirs()
                if (success) {
                    //不等于null且文件夹不存在且创建文件夹成功
                    return Ret.createSuccess(dir, "create folder success!", Ret.SuccessCode.default)

                }
                //不等于null且文件夹不存在且创建文件夹失败
                return Ret.createError(null, "create folder failed!", Ret.ErrCode.createFolderFailed)

            }

            //不等于null且文件夹存在
            return Ret.createSuccess(dir, "open folder success!", Ret.SuccessCode.default)

        }

        //等于null
        return Ret.createError(null, "open folder failed!", Ret.ErrCode.openFolderFailed)

    }

    //如果路径不存在，返回文件，如果存在，生成一个唯一名
    fun getANonExistsTarget(targetNeedCheck:File):File {
        var target = targetNeedCheck
        if(target.exists()) {
            //原始文件名
            val originFileFullPath = target.canonicalPath  //canonicalPath不会包含末尾的 / ，所以不用removeSuffix('/')

            //拆分出文件名和后缀，生成类似 "file(1).ext" 的格式，而不是 "file.ext(1)" 那样
            val extIndex = originFileFullPath.lastIndexOf('.')
            val slashIndexPlusOne = originFileFullPath.lastIndexOf('/') + 1  //此变量最小值是0，即 lastIndexOf()找不到返回-1，然后+1，等于0
            val (filePathAndName, fileExt) = if(extIndex > slashIndexPlusOne) {  //注意这里是索引大于最后一个分隔符加1，即"."之前至少有一个字符，因为如果文件名开头是"."，是隐藏文件，不将其视为后缀名，这时生成的文件名类似 ".git(1)" 而不是"(1).git"
                // 例如输入：/path/to/abc.txt, 返回 "/path/to/abc" 和 ".txt"，后缀名包含"."
                Pair(originFileFullPath.substring(0, extIndex), originFileFullPath.substring(extIndex))
            }else {
                Pair(originFileFullPath, "")
            }

            //生成文件名的最大编号，超过这个编号将会生成随机文件名
//            val max = Int.MAX_VALUE
            val max = 1000

            //for循环，直到生成一个不存在的名字
            for(i in 1..max) {
                target = File("$filePathAndName($i)$fileExt")
                if(!target.exists()) {
                    break
                }
            }
            //如果文件还存在，生成随机名
            if(target.exists()){
                while (true) {
                    target = File("$filePathAndName(${getShortUUID(len=8)})$fileExt")
                    if(!target.exists()) {
                        break
                    }
                }
            }
        }

        return target
    }


    fun getANonExistsName(name:String, exists:(String)->Boolean):String {
        var target = name
        if(exists(name)) {
            //拆分出文件名和后缀，生成类似 "file(1).ext" 的格式，而不是 "file.ext(1)" 那样
            val extIndex = name.lastIndexOf('.')
            val (fileName, fileExt) = if(extIndex > 0) {  //注意这里是索引大于最后一个分隔符加1，即"."之前至少有一个字符，因为如果文件名开头是"."，是隐藏文件，不将其视为后缀名，这时生成的文件名类似 ".git(1)" 而不是"(1).git"
                // 例如输入：/path/to/abc.txt, 返回 "/path/to/abc" 和 ".txt"，后缀名包含"."
                Pair(name.substring(0, extIndex), name.substring(extIndex))
            }else {  //无后缀或.在第一位(例如".git")
                Pair(name, "")
            }

            //生成文件名的最大编号，超过这个编号将会生成随机文件名
//            val max = Int.MAX_VALUE
            val max = 1000

            //for循环，直到生成一个不存在的名字
            for(i in 1..max) {
                target = "$fileName($i)$fileExt"
                if(!exists(target)) {
                    break
                }
            }

            //如果文件还存在，生成随机名
            if(exists(target)){
                while (true) {
                    target = "$fileName(${getShortUUID(len=8)})$fileExt"
                    if(!exists(target)) {
                        break
                    }
                }
            }
        }

        return target
    }

    //考虑要不要加个suspend？加suspend是因为拷贝大量文件时，有可能长时间阻塞，但实际上不加这方法也可运行
    fun copyOrMoveOrExportFile(srcList:List<File>, destDir:File, requireDeleteSrc:Boolean):Ret<String?> {
        //其实不管拷贝还是移动都要先拷贝，区别在于移动后需要删除源目录
        //如果发现同名，添加到同名列表，弹窗询问是否覆盖。

        if(srcList.isEmpty()) {  //例如，我选择了文件，然后对文件执行了重命名，导致已选中条目被移除，就会发生选中条目列表为空或缺少了条目的情况
            return Ret.createError(null, "srcList is empty!", Ret.ErrCode.srcListIsEmpty)  // 结束操作
        }


        //目标路径不能是文件
        if(!destDir.isDirectory || destDir.isFile) {  //其实这俩判断一个就行了，不过我看两个方法的实现不是简单的一个是另一个的取反，所以我索性两个都用了
            return Ret.createError(null, "target is a file but expect dir!", Ret.ErrCode.targetIsFileButExpectDir)
        }

        var neverShowErr = true
        //开始执行 拷贝 or 移动 or 导出
        srcList.forEach {
            val src = it
            var target:File? = null

            try {
                //1 源不能不存在(例如，我在选择模式下对某个复制到“剪贴板”的文件执行了重命名，粘贴时就会出现源不存在的情况(这种情况实际已经解决，现在20240601选中文件后重命名会把已选中列表对应条目也更新))
                //2 源和目标不能相同(否则会无限递归复制)
                //3 源不能是目标文件夹的父目录(否则会无限递归复制)
                if((!src.exists()) || (src.isDirectory && destDir.canonicalPath.startsWith(src.canonicalPath))) {
                    return@forEach  //不会终止循环而是会进入下次迭代，相当于continue
                }

                target = getANonExistsTarget(File(destDir, src.name))

                src.copyRecursively(target, false)  //false，禁用覆盖，不过，只有文件存在时才需要覆盖，而上面其实已经判断过了，所以执行到这，target肯定不存在，也用不着覆盖，但以防万一，这个值传false，避免错误覆盖文件

                if(requireDeleteSrc) {  //如果是“移动(又名“剪切”)“，则删除源
                    src.deleteRecursively()
                }

            }catch (e:Exception) {
                MyLog.e(TAG, "#copyOrMoveOrExportFile err: src=${src.canonicalPath}, target=${target?.canonicalPath}, err=${e.localizedMessage}")

                // avoid copy many files get many toasts
                if(neverShowErr) {
                    neverShowErr=false
                    Msg.requireShowLongDuration("err:${e.localizedMessage}")
                }
            }
        }

        //如果待覆盖的文件列表为空，则全部复制或移动完成，提示成功，否则弹窗询问是否覆盖
//                if(fileNeedOverrideList.isEmpty()) {
//                    Msg.requireShow(appContext.getString(R.string.success))
//                }else {  //显示询问是否覆盖文件的弹窗
//                    showOverrideFilesDialog.value = true
//                }

        //显示成功提示
        return Ret.createSuccess(null,"success")
    }

    fun saveFile(fileFullPath:String, text:String, charset:Charset=Charsets.UTF_8) {
        val fos = FileOutputStream(fileFullPath)
        val bw = fos.bufferedWriter(charset)
        //                                bw.write(editorPageShowingFileText.value)
        // 覆盖式保存文件
        bw.use {
            it.write(text)
        }
    }

    //操作成功返回成功，否则返回失败
    //这里我用Unit?代表此函数不会返回有意义的值，只会返回null
    fun saveFileAndGetResult(fileFullPath:String, text:String):Ret<Unit?> {
        try {
            saveFile(fileFullPath, text)
//            val retFileName = if(fileName.isEmpty()) getFileNameFromCanonicalPath(fileFullPath) else fileName
            return Ret.createSuccess(null)
        }catch (e:Exception) {
            MyLog.e(TAG, "#saveFileAndGetResult() err:"+e.stackTraceToString())
            return Ret.createError(null, "save file failed:${e.localizedMessage}", Ret.ErrCode.saveFileErr)
        }
    }

    fun readFile(fileFullPath: String, charset:Charset=Charsets.UTF_8):String {
        val fis = FileInputStream(fileFullPath)
        val br = fis.bufferedReader(charset)

        br.use {
            return it.readText()
        }
    }

    fun getDocumentFileFromUri(context: Context, fileUri:Uri):DocumentFile? {
        return DocumentFile.fromSingleUri(context, fileUri)
    }

    fun getFileRealNameFromUri(context: Context?, fileUri: Uri?): String? {
        if (context == null || fileUri == null) return null
        val documentFile: DocumentFile = getDocumentFileFromUri(context, fileUri) ?: return null
        val name = documentFile.name
        return if(name.isNullOrEmpty()) null else name
    }

//    fun prepareSaveToIntent() {
//        val safIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//        safIntent.addFlags(
//            Intent.FLAG_GRANT_READ_URI_PERMISSION
//                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//        )
//        startActivityForResult(safIntent, 1)
//    }

    fun recursiveExportFiles_Saf(
        contentResolver: ContentResolver,
        exportDir: DocumentFile,
        files: Array<File>,
        ignorePaths:List<String> = listOf(),
        canceled:()->Boolean = {false},
        conflictStrategy:CopyFileConflictStrategy = CopyFileConflictStrategy.RENAME,
    ) {
        if(canceled()) {
            return
        }

        val filesUnderExportDir = exportDir.listFiles()

        for(f in files) {
            if(canceled()) {
                return
            }

            //可以用来实现忽略.git目录之类的逻辑，这忽略的是源目录的文件或文件夹
            if(ignorePaths.contains(f.canonicalPath)) {
                continue
            }

            var targetName = f.name

            val targetFileBeforeCreate = filesUnderExportDir.find { it.name == targetName }

            if(targetFileBeforeCreate != null) {  //文件已存在
                if(conflictStrategy == CopyFileConflictStrategy.SKIP) {
                    continue
                }else if(conflictStrategy == CopyFileConflictStrategy.OVERWRITE_FOLDER_AND_FILE) {
                    if(targetFileBeforeCreate.isFile) {
                        targetFileBeforeCreate.delete()
                    }else {  //递归删除目录
                        recursiveDeleteFiles_Saf(contentResolver, targetFileBeforeCreate, targetFileBeforeCreate.listFiles())
                    }
                }else if(conflictStrategy == CopyFileConflictStrategy.RENAME) {
                    targetName = getANonExistsName(targetName, exists = {newName -> filesUnderExportDir.find { it.name == newName } != null})
                }
            }

            if(f.isDirectory) {
                val subDir = exportDir.createDirectory(targetName)?:continue
                val subDirFiles = f.listFiles()?:continue
                if(subDirFiles.isNotEmpty()) {
                    recursiveExportFiles_Saf(
                        contentResolver = contentResolver,
                        exportDir = subDir,
                        files = subDirFiles,
                        ignorePaths = ignorePaths,
                        canceled = canceled,
                        conflictStrategy = conflictStrategy
                    )
                }
            }else {
                val targetFile = exportDir.createFile("*/*", targetName)?:continue

                val output = contentResolver.openOutputStream(targetFile.uri)?:continue

                f.inputStream().use { ins->
                    output.use { outs ->
                        ins.copyTo(outs)
                    }
                }
            }
        }

    }

    fun recursiveImportFiles_Saf(
        contentResolver: ContentResolver,
        importDir: File,
        files: Array<DocumentFile>,
        canceled:()->Boolean = {false},
        conflictStrategy:CopyFileConflictStrategy = CopyFileConflictStrategy.RENAME,
    ) {
        if(canceled()) {
            return
        }

        val filesUnderImportDir = importDir.listFiles() ?: arrayOf<File>()

        for(f in files) {
            if(canceled()) {
                return
            }

            var targetName = f.name ?: continue

            val targetFileBeforeCreate = filesUnderImportDir.find { it.name == targetName }

            if(targetFileBeforeCreate != null) {  //文件已存在
                if(conflictStrategy == CopyFileConflictStrategy.SKIP) {
                    continue
                }else if(conflictStrategy == CopyFileConflictStrategy.OVERWRITE_FOLDER_AND_FILE) {
                    targetFileBeforeCreate.deleteRecursively()
                }else if(conflictStrategy == CopyFileConflictStrategy.RENAME) {
                    targetName = getANonExistsName(targetName, exists = {newName -> filesUnderImportDir.find { it.name == newName } != null})
                }
            }

            val target = File(importDir.canonicalPath, targetName)

            if(f.isDirectory) {
                target.mkdirs()
                val subDirFiles = f.listFiles()?:continue
                if(subDirFiles.isNotEmpty()) {
                    recursiveImportFiles_Saf(
                        contentResolver = contentResolver,
                        importDir = target,
                        files = subDirFiles,
                        canceled = canceled,
                        conflictStrategy = conflictStrategy
                    )
                }
            }else {
                target.createNewFile()

                val inputStream = contentResolver.openInputStream(f.uri)?:continue
                val outputStream = target.outputStream()
                inputStream.use { ins->
                    outputStream.use { outs ->
                        ins.copyTo(outs)
                    }
                }
            }
        }
    }

    fun recursiveDeleteFiles_Saf(contentResolver: ContentResolver, dir: DocumentFile, files: Array<DocumentFile>) {
        for(f in files) {
            if(f.isDirectory) {
                val subDirFiles = dir.listFiles()?:continue
                if(subDirFiles.isEmpty()) {
                    f.delete()
                }else {
                    recursiveDeleteFiles_Saf(contentResolver, dir, subDirFiles)
                    f.delete()
                }
            }else {
                f.delete()
            }
        }
    }

    //操作成功返回content和file的快照完整路径，否则，内容快照和文件快照，谁成功谁有路径，都不成功则都没路径但不会返回null，只是返回两个空字符串。
    //注：只有所有操作都成功才会返回成功，若返回成功但内容或文件的快照路径为空字符串，说明没请求备份对应的内容
    //如果两个请求创建备份的变量都传假，则此方法等同于单纯保存内容到targetFilePath对应的文件
    //返回值：1 保存内容到目标文件是否成功， 2 内容快照路径，若创建快照成功则非空字符串值，否则为空字符串， 3 文件快照路径，创建成功则非空字符串
    fun simpleSafeFastSave(
        content: String?,
        editorState: TextEditorState?,
        trueUseContentFalseUseEditorState: Boolean,
        targetFilePath: String,
        requireBackupContent: Boolean,
        requireBackupFile: Boolean,
        contentSnapshotFlag: SnapshotFileFlag,
        fileSnapshotFlag: SnapshotFileFlag
    ): Ret<Triple<Boolean, String, String>> {
        var contentAndFileSnapshotPathPair = Pair("","")

        try {
            val targetFile = File(targetFilePath)
            //为内容创建快照
            val contentRet = if(requireBackupContent) {
                SnapshotUtil.createSnapshotByContentAndGetResult(
                    srcFileName = targetFile.name,
                    fileContent = content,
                    editorState = editorState,
                    trueUseContentFalseUseEditorState = trueUseContentFalseUseEditorState,
                    flag = contentSnapshotFlag
                )
            }else {
                Ret.createSuccess(null, "no require backup content yet")
            }

            val fileRet = if(requireBackupFile) {
                SnapshotUtil.createSnapshotByFileAndGetResult(targetFile, fileSnapshotFlag)
            } else {
                Ret.createSuccess(null, "no require backup file yet")
            }



            //检查快照是否创建成功
            if(contentRet.hasError() && fileRet.hasError()) {
                throw RuntimeException("save content and file snapshots err")
            }

            if(contentRet.hasError()) {
                contentAndFileSnapshotPathPair = Pair("", fileRet.data?.second?:"")
                throw RuntimeException("save content snapshot err")
            }

            if(fileRet.hasError()) {
                contentAndFileSnapshotPathPair = Pair(contentRet.data?.second?:"", "")
                throw RuntimeException("save file snapshot err")
            }



            //执行到这，说明内容快照和文件快照皆创建成功，开始写入内容到目标文件（要保存的文件，一般来说也是内容的源文件）
            contentAndFileSnapshotPathPair = Pair(contentRet.data?.second?:"", fileRet.data?.second?:"")



            //将内容写入到目标文件
            if(trueUseContentFalseUseEditorState) {
                content!!.byteInputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }else {
                editorState!!.dumpLines(targetFile.outputStream())
            }

            //若请求备份content或file，则返回成功时有对应的快照路径，否则为空字符串。
            //只有保存文件成功(或者说所有操作都成功)，才会返回success，否则即使快照都保存成功，也会返回error
            val writeContentToTargetFileSuccess = true
            return Ret.createSuccess(Triple(writeContentToTargetFileSuccess, contentAndFileSnapshotPathPair.first, contentAndFileSnapshotPathPair.second))

        }catch (e:Exception) {
            MyLog.e(TAG, "#simpleSafeFastSave: err: "+e.stackTraceToString())
            //若返回错误，百分百保存文件失败或未保存，但快照可能有成功创建，需要检查对应path是否为空来判断
            val writeContentToTargetFileSuccess = false
            return Ret.createError(Triple(writeContentToTargetFileSuccess, contentAndFileSnapshotPathPair.first, contentAndFileSnapshotPathPair.second), "SSFS: save err:"+e.localizedMessage)
        }

    }

    //这个函数为了状态变量变化时能重新获取doSave，所以加了Composable？
    @Composable
    fun getDoSaveForEditor(
        editorPageShowingFilePath: MutableState<String>,
        editorPageLoadingOn: (String) -> Unit,
        editorPageLoadingOff: () -> Unit,
        appContext: Context,
        editorPageIsSaving: MutableState<Boolean>,
        needRefreshEditorPage: MutableState<String>,
        editorPageTextEditorState: CustomStateSaveable<TextEditorState>,
        pageTag: String,
        editorPageIsEdited: MutableState<Boolean>,
        requestFromParent: MutableState<String>,
        editorPageFileDto: CustomStateSaveable<FileSimpleDto>,
        isSubPageMode:Boolean,
        isContentSnapshoted: MutableState<Boolean>,
        snapshotedFileInfo: CustomStateSaveable<FileSimpleDto>,  //用来粗略判断是否已创建文件的快照，之所以说是粗略判断是因为其只能保证打开一个文件后，再不更换文件的情况下不会重复创建同一文件的快照，但一切换文件就作废了，即使已经有某个文件的快照，还是会重新创建其快照
    ): suspend () -> Unit {
        val doSave: suspend () -> Unit = doSave@{
            val funName ="doSave"  // for log

            //让页面知道正在保存文件
            editorPageIsSaving.value = true
            editorPageLoadingOn(appContext.getString(R.string.saving))

            try {
                // 先把filePath和content取出来
                val filePath = editorPageShowingFilePath.value
//                val fileContent = editorPageTextEditorState.value.getAllText()

                if (filePath.isEmpty()) {
                    //path为空content不为空的可能性不大，几乎没有
                    if(editorPageTextEditorState.value.contentIsEmpty().not() && !isContentSnapshoted.value ) {
                        MyLog.w(pageTag, "#$funName: filePath is empty, but content is not empty, will create content snapshot with a random filename...")
                        val flag = SnapshotFileFlag.editor_content_FilePathEmptyWhenSave_Backup
                        val contentSnapRet = SnapshotUtil.createSnapshotByContentWithRandomFileName(
                            fileContent = null,
                            editorState = editorPageTextEditorState.value,
                            trueUseContentFalseUseEditorState = false,
                            flag = flag
                        )
                        if (contentSnapRet.hasError()) {
                            MyLog.e(pageTag, "#$funName: create content snapshot for empty path failed:" + contentSnapRet.msg)

                            throw RuntimeException("path is empty, and save content snapshot err")
                        }else {
                            isContentSnapshoted.value=true
                            throw RuntimeException("path is empty, but save content snapshot success")
                        }

                    }

                    throw RuntimeException("path is empty!")
                }




//                changeStateTriggerRefreshPage(needRefreshEditorPage)
//            delay(10*1000)  //测试能否显示Saving...，期望能，结果能，测试通过
//        if(debugModeOn) {
//            println("editorPageTextEditorState.getStateVal="+editorPageTextEditorState.getStateVal())
//            println("editorPageTextEditorState.value.getAllText()="+editorPageTextEditorState.value.getAllText())
//        }
                //保存文件
//            println("before getAllText:"+ getSecFromTime())

                //保存前检查文件是否修改过，如果修改过，对源文件创建快照再保存
                val targetFile = File(filePath)
                // 如果要保存的那个文件已经不存在（比如被删），就不检查其是否被外部修改过了，下面直接保存即可，保存的时候会自动创建文件
                if(targetFile.exists()) {
                    //文件存在，检查是否修改过，如果修改过，创建快照，如果创建快照失败，为当前显示的内容创建快照
                    val newDto = FileSimpleDto.genByFile(targetFile)
                    //这里没必要确保dto和newDto的路径一样，创建快照的条件要宽松一些，哪怕多创建几个也比少创建几个强。（这里后面的fullPath判断其实有点多余，这里代表当前正在显示的文件读取时的初始dto，路径应和newDto的始终一致，这个dto用来判断是否重载，作为判断是否已经创建快照的dto，要不然创建完快照一更新它，再进editor的初始化代码块时，会错误认为当前显示的文件已经是最新，而不重新加载文件）
                    if(newDto.sizeInBytes!=editorPageFileDto.value.sizeInBytes || newDto.lastModifiedTime!=editorPageFileDto.value.lastModifiedTime || newDto.fullPath!=editorPageFileDto.value.fullPath) {
                        //判断已创建快照的文件信息是否和目前硬盘上的文件信息一致，注意最后一个条件判断fullPath不相同也创建快照，在这判断的话就无需在外部更新dto信息了，直接路径不一样，创建快照，更新文件信息（包含路径）就行了，而且当路径不匹配时newDto所代表的文件是save to 的对象，其内容将被覆盖，理应创建快照
                        if(snapshotedFileInfo.value.sizeInBytes != newDto.sizeInBytes || snapshotedFileInfo.value.lastModifiedTime!=newDto.lastModifiedTime || snapshotedFileInfo.value.fullPath!=newDto.fullPath) {
                            MyLog.w(pageTag, "#$funName: warn! file maybe modified by external! will create a snapshot before save...")
                            val snapRet = SnapshotUtil.createSnapshotByFileAndGetResult(targetFile, SnapshotFileFlag.editor_file_BeforeSave)
                            //连读取文件都不行，直接不保存，用户爱怎么办怎么办吧
                            //如果出错，保存content到快照，然后返回
                            //创建源文件快照出错不覆盖文件的原因：如果里面有东西，而且由于正常的原因不能创建快照，那就先不动那个文件，这样的话里面的数据不会丢，加上我在下面为content创建了快照，这样两份内容就都不会丢，比覆盖强，万一一覆盖，成功了但导致数据损失就不好了
                            if (snapRet.hasError()) {
                                //上面的调用里以及记日志了，所以这里提示用户即可
//                            Msg.requireShowLongDuration()
                                MyLog.e(pageTag, "#$funName: create file snapshot for '$filePath' failed:" + snapRet.msg)

                                //虽然为源文件创建快照失败了，但如果没有为当前内容创建快照，则为当前用户编辑的内容(content)创建个快照
                                if(editorPageTextEditorState.value.contentIsEmpty().not() && !isContentSnapshoted.value) {
                                    val contentSnapRet = SnapshotUtil.createSnapshotByContentAndGetResult(
                                        srcFileName = getFileNameFromCanonicalPath(filePath),
                                        fileContent = null,
                                        editorState = editorPageTextEditorState.value,
                                        trueUseContentFalseUseEditorState = false,
                                        flag = SnapshotFileFlag.editor_content_CreateSnapshotForExternalModifiedFileErrFallback
                                    )
                                    if (contentSnapRet.hasError()) {
                                        MyLog.e(pageTag, "#$funName: create content snapshot for '$filePath' failed:" + contentSnapRet.msg)

                                        throw RuntimeException("save origin file and content snapshots err")
                                    }else {
                                        isContentSnapshoted.value=true
                                        throw RuntimeException("save origin file snapshot err, but save content snapshot success")
                                    }

                                }else {
                                    //如果备份文件失败但内容快照已创建
                                    throw RuntimeException("save origin file snapshot err, and content is empty or snapshot already exists")
                                }


                            }else { //创建文件快照成功
                                // 更新已创建快照的文件信息
                                snapshotedFileInfo.value = newDto
                            }

                        }else {
                            MyLog.d(pageTag, "#$funName: file snapshot of '$filePath' already exists")
                        }

                        //创建快照成功，下面可以放心保存content到源文件了
                    }

                }

//            println("after getAllText, before save:"+ getSecFromTime())
//                val ret = FsUtils.saveFileAndGetResult(filePath, fileContent)

                // 保存文件。
                val ret = FsUtils.simpleSafeFastSave(
                    content = null,
                    editorState = editorPageTextEditorState.value,
                    trueUseContentFalseUseEditorState = false,
                    targetFilePath = filePath,
                    requireBackupContent = true,
                    requireBackupFile = true,
                    contentSnapshotFlag = SnapshotFileFlag.editor_content_NormalDoSave,
                    fileSnapshotFlag = SnapshotFileFlag.editor_file_NormalDoSave
                    )
//            println("after save:"+ getSecFromTime())

                //判断content快照是否成功创建，如果成功创建，更新相关变量，那样即使保存出错也不会执行后面创建内容快照的步骤了
                val (_, contentSnapshotPath, _) = ret.data
                if(contentSnapshotPath.isNotEmpty()) {  //创建内容快照成功
                    isContentSnapshoted.value=true
                }

                //注：不用更新snapshotedFileInfo，因为保存时创建的快照是修改前的文件快照，新文件必然没创建快照(不过其content已创建快照)，所以，保持snapshotedFileInfo为过期状态即可，这样下次若有必要，就会为文件创建新快照了

                //如果保存失败，且内容不为空，创建文件快照
                if (ret.hasError()) {
                    //显示提示
//                    Msg.requireShowLongDuration(ret.msg)
                    MyLog.e(pageTag, "#$funName: save file '$filePath' failed:" + ret.msg)

                    //保存失败但content不为空且之前没创建过这个content的快照，则创建content快照 (ps: content就是编辑器中的未保存的内容)
                    if (editorPageTextEditorState.value.contentIsEmpty().not() && !isContentSnapshoted.value) {
                        val snapRet = SnapshotUtil.createSnapshotByContentAndGetResult(
                            srcFileName = getFileNameFromCanonicalPath(filePath),
                            fileContent = null,
                            editorState = editorPageTextEditorState.value,
                            trueUseContentFalseUseEditorState = false,
                            flag = SnapshotFileFlag.editor_content_SaveErrFallback
                        )
                        if (snapRet.hasError()) {
                            MyLog.e(pageTag, "#$funName: save content snapshot for '$filePath' failed:" + snapRet.msg)

                            throw RuntimeException("save file and content snapshots err")
                        }else {
                            isContentSnapshoted.value=true
                            throw RuntimeException("save file err, but save content snapshot success")
                        }

                    }else {
                        MyLog.w(pageTag, "#$funName: save file failed, but content is empty or already snapshoted, so will not create snapshot for it")
                        throw RuntimeException(ret.msg)
                    }

                    //如果保存失败，isEdited仍然启用，这样就可再按保存按钮
//                    editorPageIsEdited.value = true

                } else {  //保存成功
//                    保存成功，且非子页面模式，更新下dto（子页面一律强制重载请求打开的文件，无需判断文件是否修改，而dto是用来判断文件是否修改的，因此对子页面来说，更新dto无意义）
//                    if(!isSubPageMode) {
//                    FileSimpleDto.updateDto(editorPageFileDto.value, File(filePath))

                    //执行到这里，targetFile已经修改过了，但我不确定targetFile能否获取到filePath对应的文件修改后的属性，所以新建个File对象
                    //这个是重载dto和更新快照的dto无关，所以即使创建快照成功且更新了快照dto，这个dto也依然要更新（作用好像重复了？）
                    editorPageFileDto.value = FileSimpleDto.genByFile(File(filePath))
//                    }

                    //如果保存成功，将isEdited设置为假
                    editorPageIsEdited.value = false

                    //提示保存成功
                    Msg.requireShow(appContext.getString(R.string.file_saved))
                }

//        requireShowLoadingDialog.value= false
//                editorPageIsSaving.value = false

                //保存文件后不需要重新加载文件
//                requestFromParent.value=PageRequest.needNotReloadFile

                //关闭loading
//                editorPageLoadingOff()
//                changeStateTriggerRefreshPage(needRefreshEditorPage)  //loadingOff里有刷新，所以这就不需要了


            }catch (e:Exception){

                editorPageIsEdited.value=true  //如果出异常，把isEdited设为true，这样保存按钮会重新启用，用户可再次保存
//                Msg.requireShowLongDuration(""+e.localizedMessage)  //这里不需要显示错误，外部coroutine会显示

                throw e
            }finally {
                //无论是否出异常，都把isSaving设为假，告知外部本次保存操作已执行完毕
                editorPageIsSaving.value = false

                //即使出错也会关闭Loading，不然那个正在保存的遮罩盖着，用户什么都干不了。至于异常？直接抛给调用者即可
                editorPageLoadingOff()

            }
        }
        return doSave
    }

    //删除最后修改时间超过指定天数的快照文件，注意是最后修改时间，不要按创建时间删，要不万一用户就爱编辑快照目录的文件，我给他删了，就不好了
    //folderDesc描述folder类型，为可选参数，例如folder为快照目录，则期望的folderDesc为"snapshot folder"，记日志的时候会用到此参数，理论上，不传也行，但建议传(强制！)
    fun delFilesOverKeepInDays(keepInDays: Int, folder: File, folderDesc:String) {
        val funName = "delFilesOverKeepInDays"
        try {
            MyLog.w(TAG, "#$funName: start: del expired files for '$folderDesc'")

            //把天数转换成毫秒 (当然，也可把毫秒转换成天，但是，做乘法精度比除法高，除法还有可能有余数之类的，算起来又麻烦，所以，这里用乘法)
            val keepInDaysInMillSec = keepInDays*24*60*60*1000L
            //取出UTC时区(或GMT) 1970-01-01到现在的毫秒
            val currentTimeInMillSec = System.currentTimeMillis()

            //返回true的文件将被删除
            val predicate = predicate@{f:File ->
                if(!f.isFile) {
                    return@predicate false
                }
                //文件最后修改时间，起始时间1970-01-01, 时区GMT(UTC)。（ps：最后修改时间单位默认竟然是毫秒？我一直以为是秒，不过，应该取决于平台，例如在linux上，可能记的就是秒，我也不确定）
                val lastModTimeInMillSec = f.lastModified()
                val diffInMillSec = currentTimeInMillSec - lastModTimeInMillSec
                return@predicate diffInMillSec > keepInDaysInMillSec
            }

            //执行删除
            val successDeletedCount = delFilesByPredicate(predicate, folder, folderDesc)
            MyLog.w(TAG, "#$funName: end: del expired files for '$folderDesc' done, success deleted: $successDeletedCount")

        }catch (e:Exception) {
            MyLog.e(TAG, "#$funName: del expired files for '$folderDesc' err: ${e.stackTraceToString()}")
        }
    }

    //根据predicate 删除文件，返回值为成功删除的文件数
    fun delFilesByPredicate(predicate:(File)->Boolean, folder: File, folderDesc:String):Int {
        val funName = "delFilesByPredicate"
        var successDeleteFilesCount = 0  //成功删除的文件计数

        try {
            MyLog.w(TAG, "#$funName: checking '$folderDesc' is ready for delete files or not")

            if(!folder.exists()) {
                MyLog.w(TAG, "#$funName: '$folderDesc' doesn't exist yet, operation abort")
                return successDeleteFilesCount
            }

            val files = folder.listFiles()
            if(files==null) {
                MyLog.w(TAG, "#$funName: list files for '$folderDesc' returned null, operation abort")
                return successDeleteFilesCount
            }
            if(files.isEmpty()) {
                MyLog.w(TAG, "#$funName: '$folderDesc' is empty, operation abort")
                return successDeleteFilesCount
            }

            MyLog.w(TAG, "#$funName: '$folderDesc' passed check, will start del files for it")

            for(f in files){
                try {
                    if(predicate(f)){
                        f.delete()
                        successDeleteFilesCount++
                    }
                }catch (e:Exception) {
                    MyLog.e(TAG, "#$funName: del file '${f.name}' for $folderDesc err: "+e.stackTraceToString())
                }
            }

            if(successDeleteFilesCount==0) {
                MyLog.w(TAG, "#$funName: no file need del in '$folderDesc'")
            }else {
                MyLog.w(TAG, "#$funName: deleted $successDeleteFilesCount file(s) for '$folderDesc'")
            }

            return successDeleteFilesCount
        }catch (e:Exception) {
            MyLog.e(TAG, "#$funName: del files for '$folderDesc' err: "+e.stackTraceToString())
            return successDeleteFilesCount
        }
    }

    /**
     * default disable edit files under these folders, because app may change it when running
     * 判断路径是否处于app内置的禁止编辑的文件夹中
     * 考虑：要不要在设置页面添加一个开关允许编辑这些路径下的文件？必要性不大，如果非编辑，可在内置editor手动关闭read-only或通过外部编辑器编辑，不过需要注意：如果用内部编辑器编辑有可能和app隐式更新这些文件发生冲突导致文件被错误覆盖。
     */
    @Deprecated("没必要检查是否只读，无所谓，用户编辑这些目录下的文件也不是不行，而且每次我打开这些目录下的文件，第一件事就是取消只读，不然光标无法显示，搜索后因为jetpackcompose限制难以同时实现关键字高亮和文字可编辑，搞得查找文字后就算找到了我也不知道关键字在哪，而且也没法用光标辅助定位上次我查看的位置，总之就是很恶心")
    fun deprecated_isReadOnlyDir(path: String): Boolean {
        return try {
            //app内置某些文件默认不允许编辑，因为app会在运行时编辑这些文件，有可能冲突或覆盖app自动生成的内容
            path.startsWith(AppModel.getOrCreateFileSnapshotDir().canonicalPath)
                    || path.startsWith(AppModel.getOrCreateEditCacheDir().canonicalPath)
                    || path.startsWith(AppModel.getOrCreateLogDir().canonicalPath)
                    || path.startsWith(AppModel.certBundleDir.canonicalPath)
                    || path.startsWith(AppModel.getOrCreateSettingsDir().canonicalPath)
                    || path.startsWith(AppModel.getOrCreateSubmoduleDotGitBackupDir().canonicalPath)
                    || path.startsWith(Lg2HomeUtils.getLg2Home().canonicalPath)
        }catch (e:Exception) {
            MyLog.e(TAG, "#isReadOnlyDir err:${e.stackTraceToString()}")
            false
        }

    }

    /**
     * 递归计算文件夹或文件的大小，结果会累加到itemsSize中，请在调用前自行重置其值为0
     */
    fun calculateFolderSize(fileOrFolder: File, itemsSize: MutableLongState) {
        if(fileOrFolder.isDirectory) {
            val list = fileOrFolder.listFiles()

            if(!list.isNullOrEmpty()) {
                list.forEach {
                    calculateFolderSize(it, itemsSize)
                }
            }
        }else {  // file
            itemsSize.longValue += fileOrFolder.length()
        }
    }

    /**
     * ceiling path of app, if reached those path in Files explorer, should show 'press back again to exit' rather than go to parent dir
     *
     * note: the externalFilesDir "/storage/emulated/0/Android/data/your_package_name" should not in this list,
     *   cause it actually is subdir of external storage dir "/storage/emulated/0", 不加进列表的话，切换目录方便，可直接从App的外部data目录返回到外部存储目录
     */
    fun getAppCeilingPaths():List<String> {
        return listOf(
            // usually is "/storage/emulated/0"
            getExternalStorageRootPathNoEndsWithSeparator(),
            // "/data/data/app.package.name"
            AppModel.innerDataDir.canonicalPath,
            // "/"，兜底
            rootPath
        )
    }

    /**
     * @return root path of app internal storage
     */
    fun getInternalStorageRootPathNoEndsWithSeparator():String {
        return AppModel.allRepoParentDir.canonicalPath
    }

    /**
     * @return "/storage/emulated/0" or "" if has exception
     *
     */
    fun getExternalStorageRootPathNoEndsWithSeparator():String{
        return try {
            Environment.getExternalStorageDirectory().path.removeSuffix("/")
        }catch (_:Exception) {
            ""
        }
    }

    fun getRealPathFromUri(uri:Uri):String {
        return try {
            val uriPathString = uri.path.toString()
            //eg. /storage/emulated/0/folder1/folder2
            Environment.getExternalStorageDirectory().path+File.separator +uriPathString.substring(uriPathString.indexOf(":")+1)
        }catch (_:Exception) {
            ""
        }
    }

    /**
     * @return eg. input parent="/abc/def", fullPath="/abc/def/123", will return "/123"; if fullPath not starts with parent, will return origin `fullPath`
     */
    fun getPathAfterParent(parent: String, fullPath: String): String {
        return fullPath.removePrefix(parent)
    }

    /**
     * eg: fullPath = /storage/emulated/0/repos/abc, return External:/abc
     * eg: fullPath = /storage/emulated/0/Android/path-to-app-internal-repos-folder/abc, return Internal:/abc
     * eg: fullPath = /data/data/app.package.name/files/abc, return origin path
     */
    fun getPathWithInternalOrExternalPrefix(fullPath:String, internalStorageRoot:String=FsUtils.getInternalStorageRootPathNoEndsWithSeparator(), externalStorageRoot:String=FsUtils.getExternalStorageRootPathNoEndsWithSeparator()) :String {
        return if(fullPath.startsWith(internalStorageRoot)) {  // internal storage must before external storage, because internal storage actually under external storage (eg: internal is "/storage/emulated/0/Android/data/packagename/xxx/xxxx/x", external is "/storage/emulated/0")
            internalPathPrefix+((getPathAfterParent(parent= internalStorageRoot, fullPath=fullPath)).removePrefix("/"))
        }else if(fullPath.startsWith(externalStorageRoot)) {
            externalPathPrefix+((getPathAfterParent(parent= externalStorageRoot, fullPath=fullPath)).removePrefix("/"))
        }else { //原样返回, no matched, return origin path
            fullPath
        }
    }

    fun removeInternalStoragePrefix(path: String): String {
        return path.removePrefix(internalPathPrefix)
    }

    fun removeExternalStoragePrefix(path: String): String {
        return path.removePrefix(externalPathPrefix)
    }

    fun stringToLines(string: String):List<String> {
        return string.lines()
    }

    /**
     * 替换多个行到指定行号，若源文件无EOFNL，执行完此函数后，会添加
     * replace lines to specified line number, if origin file no EOFNL, after this function, will add it
     *
     * note: if target line doesn't exist, will append content to the EOF, and delete/append content is not available for EOF
     *
     * @param file the file which would be replaced
     * @param startLineNum the line number start from 1 or -1 represent EOF, when you want to append content to the end of file, use -1 or a line number which bigger than the file actually lines is ok
     * @param startLineNum 行号可以是大于1的值或-1，如果想追加内容到文件末尾，可传-1或大于文件实际行号的值
     * @param newLines the lines will replace
     * @param trueInsertFalseReplaceNullDelete true insert , false replace, null delete
     */
    private suspend fun replaceOrInsertOrDeleteLinesToFile(file: File, startLineNum: Int, newLines: List<String>, trueInsertFalseReplaceNullDelete:Boolean?, settings: AppSettings) {
        if(trueInsertFalseReplaceNullDelete != null && newLines.isEmpty()) {
            return
        }

        if(startLineNum<1 && startLineNum!=LineNum.EOF.LINE_NUM) {
            throw RuntimeException("invalid line num")
        }

        if(file.exists().not()) {
            throw RuntimeException("target file doesn't exist")
        }

        if(settings.diff.createSnapShotForOriginFileBeforeSave) {
            val snapRet = SnapshotUtil.createSnapshotByFileAndGetResult(file, SnapshotFileFlag.diff_file_BeforeSave)
            if(snapRet.hasError()) {
                //其实加不加括号都行 ?: 优先级高于throw
                throw (snapRet.exception ?: RuntimeException(snapRet.msg.ifBlank { "err: create snapshot failed" }))
            }
        }


        val tempFile = FsUtils.createTempFile("${TempFileFlag.FROM_DIFF_SCREEN_REPLACE_LINES_TO_FILE.flag}-${file.name}")
        var found = false

        file.bufferedReader().use { reader ->
            tempFile.bufferedWriter().use { writer ->
                var currentLine = 1

                while(true) {
                    val line = reader.readLine() ?:break
                    // note: if target line num is EOF, this if never execute, but will append content to the EOF of target file after looped
                    // 注意：如果目标行号是EOF，if里的代码永远不会被执行，然后会追加内容到目标文件的末尾
                    if (currentLine++ == startLineNum) {
                        found = true

                        // delete line
                        if(trueInsertFalseReplaceNullDelete == null) {
                            continue
                        }

                        for(i in newLines.indices) {
                            writer.write(newLines[i])
                            writer.newLine()
                        }

                        // prepend(insert) line
                        if(trueInsertFalseReplaceNullDelete == true) {
                            writer.write(line)
                            writer.newLine()
                        }
                    }else {  // not match
                        writer.write(line)
                        writer.newLine()
                    }
                }

                // not found and not delete mode, append line to the end of file
                if (found.not() && trueInsertFalseReplaceNullDelete!=null) {
                    for(i in newLines.indices) {
                        writer.write(newLines[i])
                        writer.newLine()
                    }
                }
            }
        }

        // if content updated, replace the origin file
        if(found.not() && trueInsertFalseReplaceNullDelete==null){  // no update, remove temp file
            tempFile.delete()
        }else {  // updated, move temp file to origin file, temp file will delete after move
            tempFile.renameTo(file)
        }
    }

    suspend fun replaceLinesToFile(file: File, startLineNum: Int, newLines: List<String>, settings: AppSettings) {
        replaceOrInsertOrDeleteLinesToFile(file, startLineNum, newLines, trueInsertFalseReplaceNullDelete = false, settings)
    }

    suspend fun insertLinesToFile(file: File, startLineNum: Int, newLines: List<String>, settings: AppSettings) {
        prependLinesToFile(file, startLineNum, newLines, settings)
    }

    suspend fun prependLinesToFile(file: File, startLineNum: Int, newLines: List<String>, settings: AppSettings) {
        replaceOrInsertOrDeleteLinesToFile(file, startLineNum, newLines, trueInsertFalseReplaceNullDelete = true, settings)
    }

    suspend fun appendLinesToFile(file: File, startLineNum: Int, newLines: List<String>, settings: AppSettings) {
        replaceOrInsertOrDeleteLinesToFile(file, startLineNum+1, newLines, trueInsertFalseReplaceNullDelete = true, settings)
    }

    suspend fun deleteLineToFile(file: File, lineNum: Int, settings: AppSettings) {
        replaceOrInsertOrDeleteLinesToFile(file, lineNum, newLines=listOf(), trueInsertFalseReplaceNullDelete = null, settings)
    }

    fun createTempFile(prefix:String, suffix:String=".tmp"):File{
        return File(AppModel.getOrCreateExternalCacheDir().canonicalPath, "$prefix-${generateRandomString()}$suffix")
    }

    fun copy(input:InputStream, output:OutputStream) {
        val inputStream = input.bufferedReader()
        val outputStream = output.bufferedWriter()

        inputStream.use { i ->
            outputStream.use { o->
                var b = i.read()
                while(b != -1) {
                    o.write(b)
                    b = i.read()
                }
            }
        }
    }

    fun readLinesFromFile(filePath: String, addNewLineIfFileEmpty:Boolean = true): List<String> {
        return readLinesFromFile(File(filePath), addNewLineIfFileEmpty)
    }

    /**
     * 读取文件中的行到list，
     * 支持 \r 或 \n 或 \r\n 分割的文件，
     * 正常情况下，一个文件只有一种类型的换行符，用这个函数没问题，如果是个混用各种换行符的奇葩文件，行数会乱。
     *
     * @param addNewLineIfFileEmpty if true, when file is empty, will add a empty str as element to list,
     *  else will return an empty list. set it to true, if you expect this function has same behavior with `String.lines()`
     *  (如果为true，文件为空时返回只有一个空字符串元素的list，否则返回空list。如果期望此函数和`String.lines()`行为一致（空字符串返回元素1的list），此值应传true。)
     */
    fun readLinesFromFile(file: File, addNewLineIfFileEmpty:Boolean = true): List<String> {
//        val lines = ArrayList<String>(30)  //不确定用户打开的文件到底多少行啊，算了，用默认吧
        //readLines() api 说不能用于 huge files?我看源代码好像也是用readLine一行行读的，我自己写好像差不多，不过不会创建迭代器之类的，可能稍微快一点点，但应该也不能用于huge files吧？大概
//        File(filePath).bufferedReader().readLines()

        //根据文件大小估算一下行数，没任何考证，我随便打开了几个文件，感觉file.length / 1000 * 7差不多就是行数，而直接右移7位和/1000*7结果差不多，所以就这么写了，shr是有符号右移，如果是负数，移完还是负数，不看符号的话，值如果不超Int最大值，无符号和有符号右移的区别就在于符号不同，值一般是一样的
        val estimateLineCount = (file.length() shr 7).toInt().coerceAtLeast(10).coerceAtMost(10000) // chatgpt说10000个空Object对象大概470k，不会超过1mb，可以接受，不过java创建的数组默认值不是null吗？所以可能连470k都占不了
//        val estimateLineCount = (file.length() / 1000 * 7).toInt().coerceAtLeast(10).coerceAtMost(10000)

        val lines = ArrayList<String>(estimateLineCount)  //默认是ArrayList，容量10
        val arrBuf = CharArray(4096)
        val aline = StringBuilder(100)
        var lastChar:Char? = null
        file.bufferedReader().use { reader ->
            while (true) {
                val readSize = reader.read(arrBuf)
                if(readSize == -1) {
                    break
                }

                for (i in 0 until readSize) {
                    val char = arrBuf[i]
                    // handle the '\n' or '\r' or "\r\n"
                    if (char == '\n'){
                        if(lastChar != '\r') {
                            lines.add(aline.toString())
                            aline.clear()
                        }
                    } else if(char == '\r') {
                        lines.add(aline.toString())
                        aline.clear()
                    } else {
                        aline.append(char)
                    }

                    lastChar = char
                }
            }
        }

        if(aline.isNotEmpty()) {
            // last line no line break, means end of the file no new line(文件末尾没换行符）
            lines.add(aline.toString())

        // aline若为空有两种情况，1文件为空，压根没读；2读了，末尾是换行符，所以aline被清空。而lastChar若不等于null，说明至少读了一个字符，所以文件不为空，因此，aline为空且lastChar不为null就隐含文件末尾是换行符
        //其实这里用lastChar != null或判断lastChar==\n或\r都行，
        // 因为 这里隐含条件aline为空，而 `aline.isEmpty() && lastChar!=null` ，
        // 意味着文件非空(所以lastChar!=null)且最后一个字符为换行符(所以aline被清空)，但这样写不太直观，一看代码容易困惑
//        }else if(lastChar == '\n' || lastChar == '\r') { // or else if(lastChar != null)
        }else if(lastChar != null) {
            // last line, has line break
            lines.add("")
        }


        //因为用 空字符串.lines() 会返回size 1的集合，所以，若文件内容为空，也应如此
        if (addNewLineIfFileEmpty && lines.isEmpty()) {
            lines.add("")
        }

        return lines
    }


    //这个后面跟的不是路径，得用什么玩意解析一下才能拿到真名
    val contentUriPathPrefix = "content://"
    //这个后面跟的是绝对路径，例如: "file:///storage/emulated/0/yourfile.txt"
    val fileUriPathPrefix = "file://"


    /**
     * @return Pair(path, filename)
     */
    fun getFilePathAndRealNameFromUriOrCanonicalPath(context: Context, uri: Uri?): Pair<String, String> {
        if(uri == null) {
            return Pair("", "")
        }

        val uriPath = uri.path ?: ""
        if(uriPath.isBlank()) {
            return Pair("", "")
        }

        MyLog.d(TAG, "#getFilePathAndRealNameFromUriOrCanonicalPath: before parsing, uriPath=$uriPath")


        val pathAndFileNamePair = if(uriPath.startsWith("/file%3A%2F%2F%2F")){  // "/file:///" 转码过的路径，处理下，质感文件分享的路径就是转码过的
            //字符串有可能经过多次编码，所以需要循环一下，解析到无法再解析为止
            var lastPath = Uri.decode(uriPath)
            for(i in 1..1000) { // 1000次应该够了吧？
                val newPath = Uri.decode(lastPath)
                if(newPath == lastPath) {
                    break
                }

                lastPath = newPath
            }

            // rawPath like /storage/emulated/0/filename.txt
            val rawPath = lastPath.substring(8)
            Pair(rawPath, getFileNameFromCanonicalPath(rawPath))
        }else if(uriPath.startsWith(contentUriPathPrefix)) {
            //如果尝试获取uri名失败，就用File凑合下吧，虽然File获取的文件名可能乱码，不知道是乱码还是编码，反正不是给人看的
            Pair(uriPath, getFileRealNameFromUri(context, uri) ?: File(uriPath).name)
        }else if(uriPath.startsWith(fileUriPathPrefix)) {
            val path = fileUriPathPrefix.removePrefix(fileUriPathPrefix)
            Pair(path, getFileNameFromCanonicalPath(path))
        }else { // starts with / or other cases
            Pair(uriPath, getFileNameFromCanonicalPath(uriPath))
        }

        MyLog.d(TAG, "#getFilePathAndRealNameFromUriOrCanonicalPath: after parsed, path=${pathAndFileNamePair.first}, fileName=${pathAndFileNamePair.second}")

        return pathAndFileNamePair
    }

    //不确定 `rememberLauncherForActivityResult` 是否还需要这东西，如果能永久获得uri权限就不需要，否则需要。测试方法：获取一个uri访问权限，重启，如果还能访问就ok，否则不ok
    fun getPersistableUriIntent():Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) // 添加持久化标志
        }
    }

}
