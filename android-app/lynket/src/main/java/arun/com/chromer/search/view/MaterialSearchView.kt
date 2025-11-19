// Phase 8: Converted from RxJava to Kotlin Coroutines
/*
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

package arun.com.chromer.search.view

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.speech.RecognizerIntent.EXTRA_RESULTS
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import androidx.recyclerview.widget.SimpleItemAnimator
import arun.com.chromer.R
import arun.com.chromer.databinding.WidgetMaterialSearchViewBinding
import arun.com.chromer.extenstions.gone
import arun.com.chromer.search.suggestion.SuggestionController
import arun.com.chromer.search.suggestion.items.SuggestionItem.HistorySuggestionItem
import arun.com.chromer.search.suggestion.items.SuggestionType.*
import arun.com.chromer.shared.Constants.REQUEST_CODE_VOICE
import arun.com.chromer.util.Utils
import arun.com.chromer.util.animations.spring
import arun.com.chromer.util.epoxy.intercepts
import arun.com.chromer.util.glide.GlideApp
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

class MaterialSearchView
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val detachFlow = MutableSharedFlow<Unit>(replay = 1)

  private val binding: WidgetMaterialSearchViewBinding = WidgetMaterialSearchViewBinding.inflate(
    LayoutInflater.from(context),
    this,
    true
  )

  private val normalColor: Int = context.getColor(R.color.accent_icon_no_focus)
  private val focusedColor: Int = context.getColor(R.color.colorAccent)

  private val xIcon: IconicsDrawable by lazy {
    IconicsDrawable(context)
      .icon(CommunityMaterial.Icon.cmd_close)
      .color(normalColor)
      .sizeDp(16)
  }
  private val voiceIcon: IconicsDrawable by lazy {
    IconicsDrawable(context)
      .icon(CommunityMaterial.Icon.cmd_microphone)
      .color(normalColor)
      .sizeDp(18)
  }
  private val menuIcon: IconicsDrawable by lazy {
    IconicsDrawable(context)
      .icon(CommunityMaterial.Icon.cmd_menu)
      .color(normalColor)
      .sizeDp(18)
  }

  // Phase 7: TODO - Custom Views can't use @AndroidEntryPoint
  // These need to be injected manually or passed via constructor
  @Inject
  lateinit var searchPresenter: SearchPresenter

  @Inject
  lateinit var suggestionController: SuggestionController

  // Phase 7: Replaced Dagger-injected viewDetaches with local implementation
  private val viewDetaches: Flow<Unit> get() = detachFlow

  private val voiceSearchFailedFlow = MutableSharedFlow<Any>(extraBufferCapacity = 1)
  private val searchPerformedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
  private val focusChangesState = MutableStateFlow(false)

  private val searchQuery get() = if (binding.msvEditText.text == null) "" else binding.msvEditText.text.toString()

  private val searchTermChanges: Flow<CharSequence> by lazy {
    binding.msvEditText.textChangesFlow()
      .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
  }

  private val leftIconClicks: Flow<Unit> by lazy {
    binding.msvLeftIcon.clicksFlow()
      .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)
  }

  val editText: EditText get() = binding.msvEditText

  fun voiceSearchFailed(): Flow<Any> = voiceSearchFailedFlow.asSharedFlow()

  fun searchPerforms(): Flow<String> = searchPerformedFlow.asSharedFlow()
    .flatMapLatest { query ->
      flow {
        val url = searchPresenter.getSearchUrl(query)
        emit(url)
      }
    }

  fun focusChanges(): Flow<Boolean> = focusChangesState.asStateFlow()

  fun menuClicks(): Flow<Unit> = leftIconClicks
    .filter { focusChangesState.value == false }

  init {
    // Phase 7: Custom Views can't use @AndroidEntryPoint - manual injection removed
    // suggestionController must be injected externally or provided via constructor

    binding.searchSuggestions.apply {
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
      layoutManager = GridLayoutManager(context, 4, VERTICAL, true)
      setController(suggestionController)
      clipToPadding = true
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    setOnClickListener { if (!binding.msvEditText.hasFocus()) gainFocus() }
    setupEditText()
    setupLeftIcon()
    setupVoiceIcon()

    scope.launch {
      binding.msvClearIcon.clicksFlow().collect {
        binding.msvEditText.text = null
      }
    }

    setupSuggestionController()
    setupPresenter()

    // Cancel scope when view detaches
    scope.launch {
      viewDetaches.first()
      scope.cancel()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    detachFlow.tryEmit(Unit)
    scope.cancel()
  }

  override fun clearFocus() {
    clearFocus(null)
  }

  override fun hasFocus() = binding.msvEditText.hasFocus() && super.hasFocus()

  fun gainFocus() {
    handleIconsState()
    setFocusedColor()
    focusChangesState.value = true
  }

  fun loseFocus(endAction: (() -> Unit)? = null) {
    setNormalColor()
    binding.msvEditText.text = null
    hideKeyboard()
    hideSuggestions()
    focusChangesState.value = false
    handleIconsState()
    endAction?.invoke()
  }

  override fun setOnClickListener(onClickListener: OnClickListener?) = Unit

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE_VOICE) {
      when (resultCode) {
        RESULT_OK -> {
          val resultList = data?.getStringArrayListExtra(EXTRA_RESULTS)
          if (resultList != null && resultList.isNotEmpty()) {
            searchPerformed(resultList.first())
          }
        }
      }
    }
  }

  private fun setupLeftIcon() {
    class CompositeIconResource(val drawable: Drawable? = null, val uri: Uri? = null) {
      fun apply(view: ImageView) {
        when {
          drawable != null -> view.setImageDrawable(drawable)
          uri != null -> GlideApp.with(view).load(uri).into(view)
        }
      }
    }
    binding.msvLeftIcon.run {
      setImageDrawable(menuIcon)
      scope.launch {
        leftIconClicks
          .filter { focusChangesState.value == true }
          .collect {
            suggestionController.showSearchProviders = true
          }
      }
      scope.launch {
        combine(
          focusChangesState,
          searchPresenter.selectedSearchProvider
        ) { hasFocus, searchProvider ->
          if (hasFocus) {
            CompositeIconResource(uri = searchProvider.iconUri)
          } else {
            CompositeIconResource(drawable = menuIcon)
          }
        }.flowOn(Dispatchers.Default)
          .collect { iconResource -> iconResource.apply(this@run) }
      }
    }
    scope.launch {
      searchTermChanges.collect {
        suggestionController.showSearchProviders = false
      }
    }
  }

  private fun setupVoiceIcon() {
    binding.msvVoiceIcon.run {
      setImageDrawable(voiceIcon)
      setOnClickListener {
        if (searchQuery.isNotEmpty()) {
          binding.msvEditText.setText("")
          clearFocus()
        } else {
          if (Utils.isVoiceRecognizerPresent(context)) {
            (context as Activity).startActivityForResult(
              Utils.getRecognizerIntent(context),
              REQUEST_CODE_VOICE
            )
          } else {
            voiceSearchFailedFlow.tryEmit(Any())
          }
        }
      }
    }
  }

  private fun setupEditText() {
    binding.msvEditText.run {
      scope.launch {
        focusChangesFlow()
          .collect { hasFocus ->
            if (hasFocus) {
              gainFocus()
            } else {
              loseFocus()
            }
          }
      }
      scope.launch {
        editorActionFlow { it == IME_ACTION_SEARCH }
          .map { searchQuery }
          .collect(::searchPerformed)
      }
      scope.launch {
        searchTermChanges
          .collect { handleIconsState() }
      }
    }
  }

  private fun setupPresenter() {
    searchPresenter.run {
      registerSearch(searchTermChanges.map { it.toString() })

      scope.launch {
        suggestions.collect(::setSuggestions)
      }

      scope.launch {
        searchEngines.collect { searchProviders ->
          suggestionController.searchProviders = searchProviders
        }
      }

      registerSearchProviderClicks(suggestionController.searchProviderClicks)
    }
  }

  private fun setupSuggestionController() {
    scope.launch {
      suggestionController.intercepts()
        .map { it.isEmpty() }
        .collect { isEmpty ->
          binding.searchSuggestions.gone(isEmpty)
          if (!isEmpty) {
            binding.searchSuggestions.scrollToPosition(0)
          }
        }
    }

    scope.launch {
      suggestionController.suggestionClicks
        .map { suggestionItem ->
          when (suggestionItem) {
            is HistorySuggestionItem -> suggestionItem.subTitle
            else -> suggestionItem.title
          } ?: ""
        }.filter { it.isNotEmpty() }
        .flowOn(Dispatchers.Default)
        .collect(::searchPerformed)
    }

    scope.launch {
      suggestionController.suggestionLongClicks
        .filter { it.title.isNotEmpty() }
        .collect {
          binding.msvEditText.setText(it.title)
          binding.msvEditText.setSelection(it.title.length)
        }
    }
  }

  private fun clearFocus(endAction: (() -> Unit)?) {
    loseFocus(endAction)
    val view = findFocus()
    view?.clearFocus()
    super.clearFocus()
  }

  private fun hideKeyboard() {
    (context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
      windowToken,
      0
    )
  }

  private fun setFocusedColor() {
    binding.msvLeftIcon.setImageDrawable(menuIcon.color(focusedColor))
    binding.msvClearIcon.setImageDrawable(menuIcon.color(focusedColor))
    binding.msvVoiceIcon.setImageDrawable(voiceIcon.color(focusedColor))
  }

  private fun setNormalColor() {
    binding.msvLeftIcon.setImageDrawable(menuIcon.color(normalColor))
    binding.msvClearIcon.setImageDrawable(menuIcon.color(normalColor))
    binding.msvVoiceIcon.setImageDrawable(voiceIcon.color(normalColor))
  }

  private fun handleIconsState() {
    val color = if (binding.msvEditText.hasFocus()) focusedColor else normalColor
    if (searchQuery.isNotEmpty()) {
      binding.msvClearIcon.run {
        setImageDrawable(xIcon.color(color))
        spring(SpringAnimation.ALPHA).animateToFinalPosition(1F)
      }
      binding.msvVoiceIcon.setImageDrawable(voiceIcon.color(color))
    } else {
      binding.msvClearIcon.run {
        setImageDrawable(xIcon.color(color))
        spring(SpringAnimation.ALPHA).animateToFinalPosition(0F)
      }
      binding.msvVoiceIcon.setImageDrawable(voiceIcon.color(color))
    }
  }

  private fun searchPerformed(searchQuery: String) {
    Timber.d("Search performed : $searchQuery")
    clearFocus { searchPerformedFlow.tryEmit(searchQuery) }
  }

  private fun hideSuggestions() {
    suggestionController.clear()
  }

  private fun setSuggestions(suggestionResult: SuggestionResult) {
    suggestionController.query = suggestionResult.query.trim()
    val suggestion = suggestionResult.suggestions
    return when (suggestionResult.suggestionType) {
      COPY -> suggestionController.copySuggestions = suggestion
      GOOGLE -> suggestionController.googleSuggestions = suggestion
      HISTORY -> suggestionController.historySuggestions = suggestion
    }
  }

  // Flow extensions for view events
  private fun View.clicksFlow(): Flow<Unit> = callbackFlow {
    val listener = View.OnClickListener { trySend(Unit) }
    setOnClickListener(listener)
    awaitClose { setOnClickListener(null) }
  }

  private fun TextView.textChangesFlow(): Flow<CharSequence> = callbackFlow {
    // Send initial value
    trySend(text ?: "")

    val watcher = object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        trySend(s ?: "")
      }
      override fun afterTextChanged(s: Editable?) {}
    }
    addTextChangedListener(watcher)
    awaitClose { removeTextChangedListener(watcher) }
  }.drop(1) // Skip initial value

  private fun View.focusChangesFlow(): Flow<Boolean> = callbackFlow {
    val listener = View.OnFocusChangeListener { _, hasFocus ->
      trySend(hasFocus)
    }
    onFocusChangeListener = listener
    awaitClose { onFocusChangeListener = null }
  }

  private fun TextView.editorActionFlow(predicate: (Int) -> Boolean): Flow<Int> = callbackFlow {
    val listener = TextView.OnEditorActionListener { _, actionId, _ ->
      if (predicate(actionId)) {
        trySend(actionId)
        true
      } else {
        false
      }
    }
    setOnEditorActionListener(listener)
    awaitClose { setOnEditorActionListener(null) }
  }
}
