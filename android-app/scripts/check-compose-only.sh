#!/usr/bin/env bash
#
# Checks that the app is Compose-only and that the build has no kapt.
#
#  - Build files: no kapt plugin or dependency, no Kotlin Android plugin (AGP compiles Kotlin),
#    and gradle.properties has none of the temporary AGP 9 opt-out flags.
#  - Resources: res/layout holds only the Custom Tabs bottom bar layout (RemoteViews), there is
#    no res/menu folder and no preference screen XML.
#  - Sources: no class in lynket/src/main extends a View, layout, widget, Fragment, preference
#    fragment or adapter type, and no code inflates a layout or calls setContentView(R.layout...).
#
# Usage: bash android-app/scripts/check-compose-only.sh
# Prints every hit and exits 1 when there is at least one; exits 0 when the app is clean.
# Needs bash, GNU grep, find and perl (all part of Git for Windows).

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/lynket/src/main"
FAIL=0

fail() {
  FAIL=1
  echo "FAIL: $1"
  if [ -n "${2:-}" ]; then
    echo "$2" | sed 's/^/    /'
  fi
}

ok() {
  echo "ok:   $1"
}

# Every Gradle build file, the version catalog and gradle.properties (build output excluded)
mapfile -t BUILD_FILES < <(find "$ROOT" -path '*/build' -prune -o -path '*/.gradle' -prune -o -type f \
  \( -name '*.gradle' -o -name '*.gradle.kts' -o -name '*.toml' -o -name 'gradle.properties' \) -print)

search_build() {
  grep -n -H -E "$@" "${BUILD_FILES[@]}" 2>/dev/null | sed "s|^$ROOT/||"
}

# 1. kapt in any build file (plugin ids, plugin aliases, kapt( dependencies, kapt { } blocks, kapt.* flags)
hits="$(search_build -i 'kapt')"
if [ -n "$hits" ]; then fail "kapt in build files" "$hits"; else ok "no kapt in build files"; fi

# 2. The Kotlin Android plugin (AGP 9 compiles Kotlin itself)
hits="$(search_build 'org\.jetbrains\.kotlin\.android|kotlin\("android"\)|kotlin-android["'"'"']')"
if [ -n "$hits" ]; then fail "Kotlin Android plugin in build files" "$hits"; else ok "no Kotlin Android plugin"; fi

# 3. Temporary AGP 9 and migration opt-out flags in gradle.properties
OPT_OUTS='^[[:space:]]*(android\.enableAppCompileTimeRClass|android\.nonFinalResIds|android\.r8\.optimizedResourceShrinking|android\.nonTransitiveRClass[[:space:]]*=[[:space:]]*false|android\.enableJetifier|android\.newDsl|android\.builtInKotlin|kapt\.|dagger\.hilt\.disableModulesHaveInstallInCheck)'
hits="$(grep -n -E "$OPT_OUTS" "$ROOT/gradle.properties")"
if [ -n "$hits" ]; then fail "temporary opt-out flags in gradle.properties" "$hits"; else ok "no temporary opt-out flags"; fi

# 4. res/layout holds only the Custom Tabs bottom bar layout
layouts="$(ls "$APP/res/layout" 2>/dev/null | sort)"
if [ "$layouts" != "widget_bottom_bar_layout.xml" ]; then
  fail "res/layout holds more than widget_bottom_bar_layout.xml" "$(echo "$layouts" | grep -v '^widget_bottom_bar_layout.xml$')"
else
  ok "res/layout holds only widget_bottom_bar_layout.xml"
fi
other_layouts="$(find "$APP/res" -type d -name 'layout-*' 2>/dev/null)"
if [ -n "$other_layouts" ]; then fail "qualified layout folders exist" "$other_layouts"; else ok "no qualified layout folders"; fi

# 5. No menu resources
menus="$(find "$APP/res" -type d -name 'menu*' 2>/dev/null)"
if [ -n "$menus" ]; then fail "menu resource folders exist" "$menus"; else ok "no res/menu"; fi

# 6. No preference screen XML
hits="$(grep -r -l -E '<PreferenceScreen|<androidx\.preference\.' "$APP/res" 2>/dev/null)"
if [ -n "$hits" ]; then fail "preference screen XML exists" "$hits"; else ok "no preference screen XML"; fi

# 7. No class that extends a View, layout, widget, Fragment, preference fragment or adapter type.
# The class header may span lines, so each source file is read whole.
SUPERS='View|ViewGroup|SurfaceView|TextureView|FrameLayout|LinearLayout|RelativeLayout|ConstraintLayout|CoordinatorLayout|SwipeRefreshLayout|NestedScrollView|ScrollView|RecyclerView|TextView|EditText|ImageView|ImageButton|Button|CardView|MaterialCardView|AppCompat\w*View|AppCompat\w*Button|WebView|Fragment|DialogFragment|AppCompatDialogFragment|BottomSheetDialogFragment|PreferenceFragmentCompat|PreferenceFragment|Preference|\w*Adapter|EpoxyModel\w*|EpoxyHolder|EpoxyController|KotlinHolder|KotlinEpoxyModelWithHolder|ViewBindingFragment|ViewBindingActivity|BaseFragment'
hits="$(find "$APP/java" -type f \( -name '*.kt' -o -name '*.java' \) -print0 | \
  SUPERS="$SUPERS" ROOT="$ROOT" perl -0 -ne '
    BEGIN { $/ = "\0"; }
    chomp; my $file = $_;
    open(my $fh, "<", $file) or next; local $/; my $src = <$fh>; close $fh;
    my $s = $ENV{SUPERS};
    while ($src =~ /\bclass\s+\w+(?:\s*<[^>{]*>)?[^{=;]*?(?::\s*|\bextends\s+)(?:[\w.]+\.)?(?:$s)\b\s*[(<{\n]/g) {
      my $line = 1 + (() = substr($src, 0, $-[0]) =~ /\n/g);
      (my $rel = $file) =~ s/^\Q$ENV{ROOT}\E\///;
      print "$rel:$line\n";
    }
  ')"
if [ -n "$hits" ]; then fail "classes extending View, Fragment or Adapter types" "$hits"; else ok "no View, Fragment or Adapter subclasses"; fi

# 8. No layout inflation
hits="$(grep -r -n -E 'setContentView\([[:space:]]*R\.layout|setContentView\([[:space:]]*layoutRes|LayoutInflater|\.inflate\([[:space:]]*R\.layout' "$APP/java" 2>/dev/null | sed "s|^$ROOT/||")"
if [ -n "$hits" ]; then fail "layout inflation in sources" "$hits"; else ok "no setContentView(R.layout...) or layout inflation"; fi

echo
if [ "$FAIL" -ne 0 ]; then
  echo "RESULT: FAIL"
  exit 1
fi
echo "RESULT: PASS"
exit 0
