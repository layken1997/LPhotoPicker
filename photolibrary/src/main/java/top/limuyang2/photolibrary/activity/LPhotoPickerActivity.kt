package top.limuyang2.photolibrary.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.annotation.StyleRes
import android.support.v4.view.ViewCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.View
import eightbitlab.com.blurview.RenderScriptBlur
import kotlinx.android.synthetic.main.l_activity_photo_picker.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.limuyang2.photolibrary.R
import top.limuyang2.photolibrary.adapter.LPPGridDivider
import top.limuyang2.photolibrary.adapter.PhotoPickerRecyclerAdapter
import top.limuyang2.photolibrary.adapter.PhotoPickerRecyclerAdapter.Companion.CHECK_BOX_ID
import top.limuyang2.photolibrary.engine.LImageEngine
import top.limuyang2.photolibrary.model.LPhotoModel
import top.limuyang2.photolibrary.popwindow.LPhotoFolderPopWin
import top.limuyang2.photolibrary.util.*


/**
 *
 * Date 2018/7/31
 * @author limuyang
 */

@Suppress("DEPRECATION")
class LPhotoPickerActivity : LBaseActivity() {

    companion object {
        //        private const val EXTRA_CAMERA_FILE_DIR = "EXTRA_CAMERA_FILE_DIR"
        private const val EXTRA_SELECTED_PHOTOS = "EXTRA_SELECTED_PHOTOS"
        private const val EXTRA_MAX_CHOOSE_COUNT = "EXTRA_MAX_CHOOSE_COUNT"
        private const val EXTRA_PAUSE_ON_SCROLL = "EXTRA_PAUSE_ON_SCROLL"
        private const val EXTRA_COLUMNS_NUMBER = "EXTRA_COLUMNS_NUMBER"
        private const val EXTRA_IS_SINGLE_CHOOSE = "EXTRA_IS_SINGLE_CHOOSE"
        private const val EXTRA_TYPE = "EXTRA_TYPE"
        private const val EXTRA_THEME = "EXTRA_THEME"

        /**
         * 预览照片的请求码
         */
        private const val RC_PREVIEW_CODE = 2

        /**
         * 获取已选择的图片集合
         *
         * @param intent
         * @return
         */
        @JvmStatic
        fun getSelectedPhotos(intent: Intent?): ArrayList<String> {
            return intent?.getStringArrayListExtra(EXTRA_SELECTED_PHOTOS) ?: ArrayList()
        }
    }

    class IntentBuilder(context: Context) {
        private val mIntent: Intent = Intent(context, LPhotoPickerActivity::class.java)

        /**
         * 需要显示哪种类型的图片(JPG\PNG\GIF\WEBP)，默认全部加载
         * @return IntentBuilder
         */
        fun imageType(typeArray: Array<String>): IntentBuilder {
            mIntent.putExtra(EXTRA_TYPE, typeArray)
            return this
        }

        /**
         * 图片选择张数的最大值
         *
         * @param maxChooseCount
         * @return
         */
        fun maxChooseCount(maxChooseCount: Int): IntentBuilder {
            mIntent.putExtra(EXTRA_MAX_CHOOSE_COUNT, maxChooseCount)
            return this
        }

        /**
         * 是否是单选模式，默认false
         * @param isSingle Boolean
         * @return IntentBuilder
         */
        fun isSingleChoose(isSingle: Boolean): IntentBuilder {
            mIntent.putExtra(EXTRA_IS_SINGLE_CHOOSE, isSingle)
            return this
        }

        /**
         * 当前已选中的图片路径集合，可以传 null
         */
        fun selectedPhotos(selectedPhotos: java.util.ArrayList<String>?): IntentBuilder {
            mIntent.putStringArrayListExtra(EXTRA_SELECTED_PHOTOS, selectedPhotos)
            return this
        }

        /**
         * 滚动列表时是否暂停加载图片，默认为 false
         */
        fun pauseOnScroll(pauseOnScroll: Boolean): IntentBuilder {
            mIntent.putExtra(EXTRA_PAUSE_ON_SCROLL, pauseOnScroll)
            return this
        }

        /**
         * 图片选择以几列展示，默认3列
         */
        fun columnsNumber(number: Int): IntentBuilder {
            mIntent.putExtra(EXTRA_COLUMNS_NUMBER, number)
            return this
        }

        /**
         * 设置图片加载引擎
         */
        fun imageEngine(engine: LImageEngine): IntentBuilder {
            ImageEngineUtils.engine = engine
            return this
        }

        /**
         * 设置主题
         */
        fun theme(@StyleRes style: Int): IntentBuilder {
            mIntent.putExtra(EXTRA_THEME, style)
            return this
        }

        fun build(): Intent {
            return mIntent
        }
    }

    //    // 获取拍照图片保存目录
    //    private val cameraFileDir by lazy { intent.getSerializableExtra(EXTRA_CAMERA_FILE_DIR) as File }

    // 获取图片选择的最大张数
    private val maxChooseCount by lazy { intent.getIntExtra(EXTRA_MAX_CHOOSE_COUNT, 1) }

    //外部传进来的已选中的图片路径集合
    private val selectedPhotos by lazy { intent.getStringArrayListExtra(EXTRA_SELECTED_PHOTOS) }

    private val isSingleChoose by lazy { intent.getBooleanExtra(EXTRA_IS_SINGLE_CHOOSE, false) }

    //列数
    private val columnsNumber by lazy { intent.getIntExtra(EXTRA_COLUMNS_NUMBER, 3) }

    private val showTypeArray by lazy { intent.getStringArrayExtra(EXTRA_TYPE) }

    private val intentTheme by lazy { intent.getIntExtra(EXTRA_THEME, R.style.LPhotoTheme) }

    private var segmentingLineWidth: Int = 0

    private val adapter by lazy {
        val width = getScreenWidth()
        val imgWidth = (width - segmentingLineWidth * (columnsNumber + 1)) / columnsNumber

        PhotoPickerRecyclerAdapter(this, imgWidth, maxChooseCount).apply {
            setSelectedItemsPath(selectedPhotos)
        }
    }

    private val folderPopWindow by lazy {
        LPhotoFolderPopWin(this, toolBar, object : LPhotoFolderPopWin.Delegate {
            override fun onSelectedFolder(position: Int) {
                reloadPhotos(position)
            }

            override fun executeDismissAnim() {
                ViewCompat.animate(photoPickerArrow).setDuration(LPhotoFolderPopWin.ANIM_DURATION.toLong()).rotation(0f).start()
            }
        })
    }

    private val photoModelList = ArrayList<LPhotoModel>()

    override fun getLayout(): Int = R.layout.l_activity_photo_picker

    override fun getThemeId(): Int = intentTheme

    override fun initView(savedInstanceState: Bundle?) {
        initAttr()

        initRecyclerView()
        setBottomBtn()

        applyBtn.isEnabled = selectedPhotos != null && selectedPhotos.isNotEmpty()
    }

    /**
     * 获取设置的控件属性
     */
    private fun initAttr() {
        val typedArray = theme.obtainStyledAttributes(R.styleable.LPPAttr)

        val activityBg = typedArray.getColor(R.styleable.LPPAttr_l_pp_picker_activity_bg, Color.parseColor("#F9F9F9"))
        window.setBackgroundDrawable(ColorDrawable(activityBg))

        val statusBarColor = typedArray.getColor(R.styleable.LPPAttr_l_pp_status_bar_color, resources.getColor(R.color.l_pp_colorPrimaryDark))
        setStatusBarColor(statusBarColor)

        val toolBarHeight = typedArray.getDimensionPixelSize(R.styleable.LPPAttr_l_pp_toolBar_height, dp2px(56))
        val l = appBarLayout.layoutParams
        l.height = toolBarHeight
        appBarLayout.layoutParams = l

        val backIcon = typedArray.getResourceId(R.styleable.LPPAttr_l_pp_toolBar_backIcon, R.drawable.ic_l_pp_back_android)
        toolBar.setNavigationIcon(backIcon)

        val toolBarBackgroundRes = typedArray.getResourceId(R.styleable.LPPAttr_l_pp_toolBar_background, 0)
        val toolBarBackgroundColor = typedArray.getColor(R.styleable.LPPAttr_l_pp_toolBar_background, resources.getColor(R.color.l_pp_colorPrimary))

        if (toolBarBackgroundRes != 0) {
            toolBar.setBackgroundResource(toolBarBackgroundRes)
        } else {
            toolBar.setBackgroundColor(toolBarBackgroundColor)
        }

        val bottomBarBgColor = typedArray.getColor(R.styleable.LPPAttr_l_pp_picker_bottomBar_background, Color.parseColor("#96ffffff"))
        bottomLayout.setOverlayColor(bottomBarBgColor)
        val decorView = window.decorView
        //ViewGroup you want to start blur from. Choose root as close to BlurView in hierarchy as possible.
//        val rootView = decorView.findViewById<View>(android.R.id.content) as ViewGroup
        //Set drawable to draw in the beginning of each blurred frame (Optional).
        //Can be used in case your layout has a lot of transparent space and your content
        //gets kinda lost after after blur is applied.
        val windowBackground = decorView.background
        bottomLayout.setupWith(root)
                .setFrameClearDrawable(windowBackground)
                .setBlurAlgorithm(RenderScriptBlur(this))
                .setBlurRadius(25f)
                .setHasFixedTransformationMatrix(false)

        val bottomBarHeight = typedArray.getDimensionPixelSize(R.styleable.LPPAttr_l_pp_bottomBar_height, dp2px(50))
        val newBl = bottomLayout.layoutParams
        newBl.height = bottomBarHeight
        bottomLayout.layoutParams = newBl

        val bottomBarEnableTextColor = typedArray.getColor(R.styleable.LPPAttr_l_pp_picker_bottomBar_enabled_text_color, Color.parseColor("#333333"))
        val bottomBarUnEnableTextColor = typedArray.getColor(R.styleable.LPPAttr_l_pp_picker_bottomBar_unEnabled_text_color, Color.GRAY)
        val colors = intArrayOf(bottomBarEnableTextColor, bottomBarUnEnableTextColor)
        val states = arrayOfNulls<IntArray>(2)
        states[0] = intArrayOf(android.R.attr.state_enabled)
        states[1] = intArrayOf(android.R.attr.state_window_focused)
        val colorList = ColorStateList(states, colors)
        previewBtn.setTextColor(colorList)
        applyBtn.setTextColor(colorList)

        val titleColor = typedArray.getColor(R.styleable.LPPAttr_l_pp_toolBar_title_color, Color.WHITE)
        val titleSize = typedArray.getDimension(R.styleable.LPPAttr_l_pp_toolBar_title_size, dp2px(16).toFloat())
        photoPickerTitle.setTextColor(titleColor)
        photoPickerTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize)
        photoPickerArrow.setColorFilter(titleColor)

        segmentingLineWidth = typedArray.getDimensionPixelOffset(R.styleable.LPPAttr_l_pp_picker_segmenting_line_width, dp2px(5))

        typedArray.recycle()
    }

    private fun initRecyclerView() {
        pickerRecycler.apply {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@LPhotoPickerActivity, columnsNumber)
            adapter = this@LPhotoPickerActivity.adapter
            if (isSingleChoose) {
                addItemDecoration(LPPGridDivider(segmentingLineWidth, columnsNumber))
                bottomLayout.visibility = View.GONE
            } else {
                addItemDecoration(LPPGridDivider(segmentingLineWidth, columnsNumber, bottomLayout.layoutParams.height))
            }

            if (intent.getBooleanExtra(EXTRA_PAUSE_ON_SCROLL, false)) {
                addOnScrollListener(LPPOnScrollListener(this@LPhotoPickerActivity))
            }
        }
    }

    override fun initListener() {
        toolBar.setNavigationOnClickListener { finish() }
        titleLayout.singleClick {
            showPhotoFolderPopWindow()
        }

        previewBtn.singleClick {
            gotoPreview()
        }

        applyBtn.singleClick {
            returnSelectedPhotos(adapter.getSelectedItems())
        }

        adapter.onPhotoItemClick = { view, path, _ ->
            if (isSingleChoose) {
                val list = ArrayList<String>().apply { add(path) }
                returnSelectedPhotos(list)
            } else {
                adapter.setChooseItem(path, view.findViewById(CHECK_BOX_ID))
                setBottomBtn()
            }
        }

    }

    override fun initData() {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                val list = findPhoto(this@LPhotoPickerActivity, showTypeArray)
                this@LPhotoPickerActivity.photoModelList.addAll(list)
            }
            reloadPhotos(0)
        }
    }

    private fun reloadPhotos(pos: Int) {
        if (photoModelList.size > 0 && photoModelList.size >= pos) {
            photoPickerTitle.text = photoModelList[pos].name
            adapter.setData(photoModelList[pos].photoInfoList)
        }
    }

    private fun showPhotoFolderPopWindow() {
        folderPopWindow.setData(photoModelList)
        folderPopWindow.show()

        ViewCompat.animate(photoPickerArrow).setDuration(LPhotoFolderPopWin.ANIM_DURATION.toLong()).rotation(-180f).start()
    }

    @SuppressLint("SetTextI18n")
    private fun setBottomBtn() {
        if (adapter.hasSelected()) {
            applyBtn.isEnabled = true
            applyBtn.text = "${getString(R.string.l_pp_apply)}(${adapter.getSelectedItemSize()}/$maxChooseCount)"

            previewBtn.isEnabled = true
        } else {
            applyBtn.isEnabled = false
            applyBtn.text = getString(R.string.l_pp_apply)

            previewBtn.isEnabled = false
        }
    }

    /**
     * 返回已选中的图片集合
     *
     * @param selectedPhotos
     */
    private fun returnSelectedPhotos(selectedPhotos: ArrayList<String>) {
        val intent = Intent()
        intent.putStringArrayListExtra(EXTRA_SELECTED_PHOTOS, selectedPhotos)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun gotoPreview() {
        val intent = LPhotoPickerPreviewActivity.IntentBuilder(this)
                .maxChooseCount(maxChooseCount)
                .selectedPhotos(adapter.getSelectedItems())
                .theme(intentTheme)
                .isFromTakePhoto(false)
                .build()
        startActivityForResult(intent, RC_PREVIEW_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RC_PREVIEW_CODE -> {
                when (resultCode) {
                    Activity.RESULT_CANCELED -> {
                        data?.let {
                            adapter.setSelectedItemsPath(LPhotoPickerPreviewActivity.getSelectedPhotos(it))
                            setBottomBtn()
                        }
                    }

                    Activity.RESULT_OK -> {
                        data?.let {
                            returnSelectedPhotos(LPhotoPickerPreviewActivity.getSelectedPhotos(it))
                        }
                    }
                }
            }
        }

    }

    /**
     * recyclerView 滑动监听，滑动时暂停加载图片
     * @property context Context
     * @constructor
     */
    private class LPPOnScrollListener(private val context: Context) : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                ImageEngineUtils.engine.resume(context)
            } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                ImageEngineUtils.engine.pause(context)
            }
        }
    }
}