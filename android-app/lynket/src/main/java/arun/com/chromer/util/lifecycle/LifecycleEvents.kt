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

package arun.com.chromer.util.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import dev.arunkumar.android.dagger.activity.PerActivity
import dev.arunkumar.android.dagger.fragment.PerFragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Qualifier

open class LifecycleEvents constructor(lifecycleOwner: LifecycleOwner) : LifecycleObserver {

  private val lifecycleEventFlow = MutableSharedFlow<Lifecycle.Event>(extraBufferCapacity = 1)

  val lifecycles: Flow<Lifecycle.Event> = lifecycleEventFlow

  init {
    lifecycleOwner.lifecycle.addObserver(this)
  }

  @OnLifecycleEvent(ON_CREATE)
  fun onCreate() {
    lifecycleEventFlow.tryEmit(ON_CREATE)
  }

  @OnLifecycleEvent(ON_RESUME)
  fun onResume() {
    lifecycleEventFlow.tryEmit(ON_RESUME)
  }

  @OnLifecycleEvent(ON_START)
  fun onStart() {
    lifecycleEventFlow.tryEmit(ON_START)
  }

  @OnLifecycleEvent(ON_PAUSE)
  fun onPause() {
    lifecycleEventFlow.tryEmit(ON_PAUSE)
  }

  @OnLifecycleEvent(ON_STOP)
  fun onStop() {
    lifecycleEventFlow.tryEmit(ON_STOP)
  }

  @OnLifecycleEvent(ON_DESTROY)
  fun onDestroy() {
    lifecycleEventFlow.tryEmit(ON_DESTROY)
  }

  val created: Flow<Lifecycle.Event> = lifecycleEventFlow.filter { it == ON_CREATE }
  val resumes: Flow<Lifecycle.Event> = lifecycleEventFlow.filter { it == ON_RESUME }
  val starts: Flow<Lifecycle.Event> = lifecycleEventFlow.filter { it == ON_START }
  val pauses: Flow<Lifecycle.Event> = lifecycleEventFlow.filter { it == ON_PAUSE }
  val stops: Flow<Lifecycle.Event> = lifecycleEventFlow.filter { it == ON_STOP }
  val destroys: Flow<Lifecycle.Event> = lifecycleEventFlow.filter { it == ON_DESTROY }
}

@Qualifier
annotation class ActivityLifecycle

@Qualifier
annotation class FragmentLifcecycle

@PerActivity
class ActivityLifecycleEvents
@Inject
constructor(@ActivityLifecycle lifecycleOwner: LifecycleOwner) : LifecycleEvents(lifecycleOwner)

@PerFragment
class FragmentLifecycle
@Inject
constructor(@FragmentLifcecycle lifecycleOwner: LifecycleOwner) : LifecycleEvents(lifecycleOwner)
