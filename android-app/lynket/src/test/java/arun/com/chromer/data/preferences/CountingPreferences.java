/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.data.preferences;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stored settings that count each key looked up and fail on a copy of the whole map.
 *
 * <p>Written in Java: the constructor of {@link Preferences} is internal to Kotlin callers, but
 * public in the class file (javap of datastore-preferences-core 1.3.0).
 */
final class CountingPreferences extends Preferences {
  private final MutablePreferences stored;
  final List<Preferences.Key<?>> keysRead = new ArrayList<>();

  CountingPreferences(MutablePreferences stored) {
    this.stored = stored;
  }

  @Override
  public <T> boolean contains(Preferences.Key<T> key) {
    return stored.contains(key);
  }

  @Override
  public <T> T get(Preferences.Key<T> key) {
    keysRead.add(key);
    return stored.get(key);
  }

  @Override
  public Map<Preferences.Key<?>, Object> asMap() {
    throw new AssertionError("a settings read copied the stored map");
  }
}
