#!/usr/bin/env bash
# 固定源码构建 Android PIC 静态依赖，并将 JNI 与之链接为单一 libplayer.so。
set -Eeuo pipefail

script_dir=$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
repo_root=$(CDPATH='' cd -- "$script_dir/../.." && pwd -P)

usage() {
  cat <<'HELP'
用法：ANDROID_NDK_HOME=/path/to/android-ndk-r29 bash tools/mpv-static/build.sh [选项]

选项：
  仅构建 arm64-v8a，不提供其他目标架构选项。
  --output DIR     libplayer.so 的输出根目录；默认 core/playback/src/main/jniLibs
  --work-dir DIR   构建工作区；优先于 MPV_WORK_DIR，默认 tools/mpv-static/.work
  --jobs N         并行编译数；默认使用在线 CPU 数
  -h, --help       显示本说明；不下载或执行构建

主机要求：Linux x86_64 或 WSL2、Android NDK r29 (29.0.14206865)、
Meson >= 1.6.1、Ninja、CMake >= 3.18、Python 3、宿主 C 编译器、
GNU make、gperf、pkg-config/pkgconf、curl、GNU tar、xz、sha256sum 和 coreutils。
NDK、主机工具均须事先由使用者安装；本脚本不安装系统软件或执行远程安装器。
为保证跨编译工具可解析，NDK 和工作区路径不得含空白或单引号。

示例：
  ANDROID_NDK_HOME="$HOME/android/ndk/29.0.14206865" \
    bash tools/mpv-static/build.sh --jobs 4
  MPV_WORK_DIR="$HOME/.cache/player-mpv" ANDROID_NDK_HOME=/opt/android-ndk-r29 \
    bash tools/mpv-static/build.sh --output /tmp/jniLibs

输出 DIR/<ABI>/libplayer.so；许可文本及构建说明位于 DIR.licenses/。
校验缓存位于工作区 downloads/；每次在独立 run-* 目录中编译，保留对应源码、
静态库及 JNI 对象文件。将 LGPL 二进制分发给他人时，须结合许可证提供相应源码、
修改和允许用户替换 LGPL 库后重新链接的材料；保留完整工作区和本构建脚本。
仅保留许可文本并不自动满足这些义务。详见 NOTICE.txt 及源码中的各许可证。
采用 FreeType License 时产品文档须明确致谢 FreeType Project，见 FTL.TXT。

固定依赖及精确下载地址/SHA-256 见 sources.tsv：mpv 0.41.0、FFmpeg 8.1、
libplacebo 7.360.1、FreeType 2.14.3、FriBidi 1.0.16、HarfBuzz 14.1.0、
libass 0.17.4、Fontconfig 2.17.1、Expat 2.10.0、libunibreak 6.1，以及
libplacebo 所需的固定 glad/Jinja/MarkupSafe/fast_float 提交。禁用 GPL/非自由
构建选项、Vulkan、FFmpeg 网络协议及无关组件；保留 file、pipe 和 Android
content 协议，提供 Android MediaCodec、EGL、音频、本地文件解码和字幕。
关闭 FFmpeg 网络协议不影响应用自身独立实现的网页上传服务。
字体使用 Android 系统字体；设备与文件的支持仍需实机验证。
FFmpeg 与 libass 启用 ARM64 汇编优化；未剥离的原生库保存在构建工作区。
固定 SOURCE_DATE_EPOCH=1773446400；输出可能包含工作区绝对路径，且宿主工具版本
未完全锁定，因此不承诺跨机器逐字节一致。需保存工作区的 TOOLS.txt。
HELP
}

die() { printf '错误：%s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "缺少宿主工具：$1"; }

output="$repo_root/core/playback/src/main/jniLibs"
work_dir=${MPV_WORK_DIR:-"$script_dir/.work"}
jobs=''
while (($#)); do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --output|--work-dir|--jobs)
      (($# >= 2)) || die "$1 需要参数"
      case "$1" in
        --output) output=$2 ;;
        --work-dir) work_dir=$2 ;;
        --jobs) jobs=$2 ;;
      esac
      shift 2 ;;
    *) die "未知参数：$1；使用 --help 查看用法" ;;
  esac
done

[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || die '需要 Linux/WSL x86_64 主机'
for cmd in bash curl sha256sum tar xz make ninja meson cmake python3 gperf pkg-config cc find cp mktemp; do need "$cmd"; done
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
[[ -n $ndk && -d $ndk ]] || die '请设置 ANDROID_NDK_HOME，指向 Linux 版 Android NDK r29'
ndk=$(CDPATH='' cd -- "$ndk" && pwd -P)
[[ -f $ndk/source.properties ]] || die 'NDK 缺少 source.properties'
grep -Eq '^Pkg\.Revision[[:space:]]*=[[:space:]]*29\.0\.14206865[[:space:]]*$' "$ndk/source.properties" || die '请使用固定版本 NDK 29.0.14206865'
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64"
[[ -x $toolchain/bin/clang && -x $toolchain/bin/llvm-readelf ]] || die '未找到 Linux NDK LLVM 工具链'
[[ -n $jobs ]] || jobs=$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf '4')
[[ $jobs =~ ^[1-9][0-9]*$ ]] || die '--jobs 须为正整数'
mkdir -p -- "$work_dir" "$output"
work_dir=$(CDPATH='' cd -- "$work_dir" && pwd -P)
output=$(CDPATH='' cd -- "$output" && pwd -P)
[[ $ndk != *[[:space:]\']* && $work_dir != *[[:space:]\']* ]] || die 'NDK 和工作区路径不得含空白或单引号'
mkdir -p -- "$work_dir/downloads"

# 禁止从宿主机隐式混入头文件、库、Python 模块或 pkg-config 路径。
unset CC CXX CFLAGS CXXFLAGS CPPFLAGS LDFLAGS CPATH LIBRARY_PATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH PKG_CONFIG_PATH PKG_CONFIG_LIBDIR PKG_CONFIG_SYSROOT_DIR CMAKE_PREFIX_PATH PYTHONPATH
export PYTHONNOUSERSITE=1 SOURCE_DATE_EPOCH=1773446400 LC_ALL=C TZ=UTC

# 只接受清单中的 HTTPS 压缩包；缓存也必须每次校验。
while IFS=$'\t' read -r name digest url; do
  [[ -z $name || $name == \#* ]] && continue
  [[ $name =~ ^[a-z0-9_]+\.tar\.(gz|xz)$ && $digest =~ ^[a-f0-9]{64}$ && $url == https://* ]] || die '源码清单格式无效'
  archive="$work_dir/downloads/$name"
  if [[ ! -f $archive ]]; then
    part="$archive.part"
    rm -f -- "$part"
    curl --fail --location --proto '=https' --proto-redir '=https' --tlsv1.2 --retry 3 --output "$part" "$url"
    printf '%s  %s\n' "$digest" "$part" | sha256sum --check --status || { rm -f -- "$part"; die "下载校验失败：$name"; }
    mv -- "$part" "$archive"
  fi
  printf '%s  %s\n' "$digest" "$archive" | sha256sum --check --status || die "缓存校验失败：$archive；请手工删除后重试"
done < "$script_dir/sources.tsv"

run_dir=$(mktemp -d "$work_dir/run-XXXXXXXX")
src="$run_dir/sources"
mkdir -p -- "$src"
# 保存参与本次构建的 JNI 源码和配方，避免工作区与之后的仓库修改产生歧义。
mkdir -p -- "$run_dir/recipe"
cp -a -- "$script_dir/jni" "$run_dir/jni"
cp -- "$script_dir/build.sh" "$script_dir/sources.tsv" "$script_dir/NOTICE.txt" \
  "$script_dir/LICENSE.libmpv-android" "$run_dir/recipe/"
{
  printf 'NDK: 29.0.14206865\nABI: arm64-v8a\nAPI: 26\nSOURCE_DATE_EPOCH: %s\n' "$SOURCE_DATE_EPOCH"
  printf 'meson: '; meson --version
  printf 'ninja: '; ninja --version
  cmake --version
  python3 --version
  pkg-config --version
  gperf --version
  cc --version
  "$toolchain/bin/clang" --version
} > "$run_dir/TOOLS.txt"
while IFS=$'\t' read -r name digest url; do
  [[ -z $name || $name == \#* ]] && continue
  component=${name%%.tar.*}
  mkdir -p -- "$src/$component"
  tar -xf "$work_dir/downloads/$name" --strip-components=1 -C "$src/$component"
done < "$script_dir/sources.tsv"
# GitHub 自动生成的主包不含 git 子模块；解压已单独校验的原始子模块。
for component in glad jinja markupsafe fast_float; do
  mkdir -p -- "$src/libplacebo/3rdparty/$component"
  cp -a -- "$src/$component/." "$src/libplacebo/3rdparty/$component/"
done

# 将源码提供的许可证和版权文件与二进制分开放置，保留相对路径避免覆盖。
license_dir="${output}.licenses"
mkdir -p -- "$license_dir"
cp -- "$script_dir/NOTICE.txt" "$script_dir/sources.tsv" "$script_dir/LICENSE.libmpv-android" "$license_dir/"
while IFS= read -r -d '' file; do
  relative=${file#"$src/"}
  mkdir -p -- "$license_dir/sources/$(dirname -- "$relative")"
  cp -- "$file" "$license_dir/sources/$relative"
done < <(find "$src" -type f \( -iname 'license*' -o -iname 'licence*' -o -iname 'copying*' -o -iname 'copyright*' -o -iname 'notice*' -o -iname 'ftl.txt' \) -print0)
# FreeType 内部 zlib 的许可正文位于头文件而非独立 LICENSE 文件。
mkdir -p -- "$license_dir/sources/freetype/src/gzip"
cp -- "$src/freetype/src/gzip/zlib.h" "$license_dir/sources/freetype/src/gzip/zlib.h"
# 还要保存随 NDK 提供的 LLVM/libc++ 与其他工具链许可；缺失时必须醒目标出。
ndk_notices=0
while IFS= read -r -d '' file; do
  relative=${file#"$ndk/"}
  mkdir -p -- "$license_dir/ndk/$(dirname -- "$relative")"
  cp -- "$file" "$license_dir/ndk/$relative"
  ((ndk_notices+=1))
done < <(find "$ndk" -maxdepth 8 -type f \( -iname 'notice*' -o -iname 'license*' -o -iname 'copying*' \) -print0)
if ((ndk_notices == 0)); then
  printf '%s\n' '警告：未在所给 NDK 中找到 NOTICE/LICENSE；分发前请补齐 LLVM/libc++/运行时许可。' \
    | tee "$license_dir/NDK-NOTICE-MISSING.txt" >&2
fi
printf '构建工作区：%s\n' "$run_dir" | tee "$license_dir/BUILD-LOCATION.txt"

build_arm64() {
  local abi=arm64-v8a triple=aarch64-linux-android family=aarch64 cpu=aarch64 arch=aarch64
  local cc cxx prefix build cross config_dir source object
  local -a objects=() archives=()
  cc="$toolchain/bin/${triple}26-clang"
  cxx="$toolchain/bin/${triple}26-clang++"
  [[ -x $cc && -x $cxx ]] || die "NDK 不支持 ABI：$abi"
  prefix="$run_dir/$abi/prefix"
  build="$run_dir/$abi/build"
  cross="$run_dir/$abi/cross.txt"
  mkdir -p -- "$prefix/lib/pkgconfig" "$prefix/include" "$build"
  export PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig"
  cat > "$cross" <<CROSS
[binaries]
c = '$cc'
cpp = '$cxx'
ar = '$toolchain/bin/llvm-ar'
strip = '$toolchain/bin/llvm-strip'
pkg-config = 'pkg-config'
[host_machine]
system = 'android'
cpu_family = '$family'
cpu = '$cpu'
endian = 'little'
[built-in options]
buildtype = 'release'
default_library = 'static'
b_staticpic = true
wrap_mode = 'nodownload'
c_args = ['-fPIC']
cpp_args = ['-fPIC']
c_link_args = ['-Wl,-z,max-page-size=16384']
cpp_link_args = ['-static-libstdc++', '-Wl,-z,max-page-size=16384']
CROSS
  meson_build() {
    local name=$1; shift
    meson setup "$build/$name" "$src/$name" --cross-file "$cross" \
      --prefix "$prefix" --libdir lib --wrap-mode nodownload -Dauto_features=disabled "$@"
    ninja -C "$build/$name" -j "$jobs"
    ninja -C "$build/$name" install
  }

  # FreeType 不使用外部 PNG/Brotli 或 HarfBuzz，避免交叉依赖。
  meson_build freetype -Dharfbuzz=disabled -Dzlib=internal -Dmmap=enabled \
    -Dpng=disabled -Dbzip2=disabled -Dbrotli=disabled -Dtests=disabled
  meson_build fribidi -Ddocs=false -Dtests=false -Dbin=false
  meson_build harfbuzz -Dfreetype=enabled -Dcairo=disabled -Dgpu=disabled \
    -Draster=disabled -Dvector=disabled -Dsubset=disabled -Dtests=disabled \
    -Ddocs=disabled -Dutilities=disabled -Dglib=disabled -Dicu=disabled

  cmake -S "$src/expat/expat" -B "$build/expat" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
    -DCMAKE_INSTALL_PREFIX="$prefix" -DCMAKE_INSTALL_LIBDIR=lib \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_BUILD_TYPE=Release \
    -DEXPAT_SHARED_LIBS=OFF -DEXPAT_BUILD_TOOLS=OFF -DEXPAT_BUILD_EXAMPLES=OFF \
    -DEXPAT_BUILD_TESTS=OFF -DEXPAT_BUILD_DOCS=OFF -DEXPAT_BUILD_PKGCONFIG=ON
  cmake --build "$build/expat" --parallel "$jobs"
  cmake --install "$build/expat"

  meson_build fontconfig -Dxml-backend=expat -Dfontations=disabled \
    -Dtests=disabled -Ddoc=disabled -Dtools=disabled -Dnls=disabled \
    -Dcache-build=disabled -Ddefault-fonts-dirs=/system/fonts,/product/fonts \
    -Dadditional-fonts-dirs=no

  # 与 libunibreak 6.1 的 src/Makefile.am 相同的源码清单，不触发联网生成规则。
  mkdir -p -- "$build/libunibreak"
  for source in unibreakbase unibreakdef linebreak linebreakdata linebreakdef eastasianwidthdef emojidef graphemebreak wordbreak; do
    object="$build/libunibreak/$source.o"
    "$cc" -std=c99 -O2 -fPIC -I"$src/libunibreak/src" -c "$src/libunibreak/src/$source.c" -o "$object"
    objects+=("$object")
  done
  "$toolchain/bin/llvm-ar" rcs "$prefix/lib/libunibreak.a" "${objects[@]}"
  for source in unibreakbase.h unibreakdef.h linebreak.h linebreakdef.h eastasianwidthdef.h graphemebreak.h wordbreak.h; do
    cp -- "$src/libunibreak/src/$source" "$prefix/include/$source"
  done
  cat > "$prefix/lib/pkgconfig/libunibreak.pc" <<PC
prefix=$prefix
libdir=$prefix/lib
includedir=$prefix/include
Name: libunibreak
Description: Unicode line, word and grapheme breaking
Version: 6.1
Libs: -L$prefix/lib -lunibreak
Cflags: -I$prefix/include
PC
  meson_build libass -Dfontconfig=enabled -Dlibunibreak=enabled -Dasm=enabled \
    -Dtest=disabled -Dcompare=disabled -Dprofile=disabled -Dfuzz=disabled -Dcheckasm=disabled

  # 使用交叉编译器检查 Android API；保留常见软解码器、解析器和容器。
  mkdir -p -- "$build/ffmpeg"
  (
    cd -- "$build/ffmpeg"
    "$src/ffmpeg/configure" --prefix="$prefix" --libdir="$prefix/lib" \
      --target-os=android --enable-cross-compile --arch="$arch" \
      --cc="$cc" --cxx="$cxx" --ld="$cc" --ar="$toolchain/bin/llvm-ar" \
      --nm="$toolchain/bin/llvm-nm" --ranlib="$toolchain/bin/llvm-ranlib" \
      --strip="$toolchain/bin/llvm-strip" --pkg-config=pkg-config \
      --enable-pic --enable-static --disable-shared --disable-programs --disable-doc \
      --disable-autodetect --disable-iconv --enable-zlib --enable-jni --enable-mediacodec \
      --disable-vulkan --disable-network --enable-protocol=file,pipe,android_content \
      --disable-avdevice --disable-stripping
    make -j"$jobs"
    make install
  )

  meson_build libplacebo -Dopengl=enabled -Dgl-proc-addr=enabled \
    -Dvulkan=disabled -Dglslang=disabled -Dshaderc=disabled -Ddemos=false -Dtests=false
  meson_build mpv -Dgpl=false -Dlibmpv=true -Dcplayer=false -Dbuild-date=false \
    -Dlua=disabled -Diconv=disabled -Dgl=enabled -Degl-android=enabled \
    -Dandroid-media-ndk=enabled -Daudiotrack=enabled -Daaudio=enabled \
    -Dopensles=enabled -Dvulkan=disabled -Dmanpage-build=disabled

  # JNI 对象单独保留，方便分发者满足静态 LGPL 重新链接的要求。
  objects=()
  shopt -s nullglob
  local -a cpp_sources=("$run_dir"/jni/*.cpp)
  shopt -u nullglob
  ((${#cpp_sources[@]} > 0)) || die '未找到 JNI C++ 源文件'
  mkdir -p -- "$build/jni"
  for source in "${cpp_sources[@]}"; do
    object="$build/jni/$(basename -- "${source%.cpp}").o"
    "$cxx" -std=c++20 -O2 -fPIC -I"$prefix/include" -I"$run_dir/jni" -c "$source" -o "$object"
    objects+=("$object")
  done
  for source in mpv ass avfilter avformat avcodec swresample swscale avutil placebo harfbuzz fontconfig freetype fribidi unibreak expat; do
    [[ -f "$prefix/lib/lib$source.a" ]] || die "缺少静态库：lib$source.a ($abi)"
    archives+=("$prefix/lib/lib$source.a")
  done
  local so="$build/libplayer.so"
  local -a link_command=("$cxx" -shared -static-libstdc++ -Wl,--no-undefined -Wl,--exclude-libs,ALL
    -Wl,-z,max-page-size=16384 -Wl,-soname,libplayer.so -o "$so"
    "${objects[@]}" -Wl,--start-group "${archives[@]}" -Wl,--end-group
    -lOpenSLES -lEGL -lGLESv2 -landroid -llog -lmediandk -lz -ldl -lm -latomic)
  printf '%q ' "${link_command[@]}" > "$build/relink.sh"
  printf '\n' >> "$build/relink.sh"
  "${link_command[@]}"

  # 动态接口与内部编解码器均须存在；不依赖独立 mpv/FFmpeg/libc++ 动态库。
  local dynamic_symbols="$build/dynamic-symbols.txt" all_symbols="$build/all-symbols.txt" needed_file="$build/needed.txt" symbol line needed found=0
  "$toolchain/bin/llvm-nm" -D --defined-only "$so" > "$dynamic_symbols"
  "$toolchain/bin/llvm-nm" --defined-only "$so" > "$all_symbols"
  for symbol in nativeCreate nativeInit nativeDestroy nativeCommand nativeAttachSurface nativeDetachSurface nativeSetOptionString nativeGetPropertyInt nativeSetPropertyInt nativeGetPropertyDouble nativeSetPropertyDouble nativeGetPropertyBoolean nativeSetPropertyBoolean nativeGetPropertyString nativeSetPropertyString nativeObserveProperty; do
    grep -Eq "[[:space:]]Java_dev_jdtech_mpv_MPVLib_${symbol}$" "$dynamic_symbols" || die "JNI 符号缺失：$symbol ($abi)"
  done
  for symbol in mpv_create mpv_initialize mpv_set_option av_jni_set_java_vm av_jni_set_android_app_ctx ff_h264_decoder ff_hevc_decoder ff_av1_decoder ff_vp9_decoder ff_h264_mediacodec_decoder ff_file_protocol ff_pipe_protocol ff_android_content_protocol; do
    grep -Eq "[[:space:]]${symbol}$" "$all_symbols" || die "核心符号缺失：$symbol ($abi)"
  done
  "$toolchain/bin/llvm-readelf" -d "$so" > "$needed_file"
  while IFS= read -r line; do
    [[ $line == *'(NEEDED)'* ]] || continue
    [[ $line =~ \[([^]]+)\] ]] || die "无法解析 DT_NEEDED：$line"
    needed=${BASH_REMATCH[1]}; found=1
    case "$needed" in
      libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libEGL.so|libGLESv2.so|libOpenSLES.so|libmediandk.so|libaaudio.so|libz.so) ;;
      *) die "发现非白名单动态依赖：$needed ($abi)" ;;
    esac
  done < "$needed_file"
  ((found)) || die "未找到 Android 系统动态依赖 ($abi)"
  mkdir -p -- "$output/$abi"
  "$toolchain/bin/llvm-strip" --strip-unneeded -o "$output/$abi/libplayer.so" "$so"
  printf '已生成 %s\n' "$output/$abi/libplayer.so"
}

build_arm64
printf '完成；请保存工作区 %s 与许可证目录 %s。\n' "$run_dir" "$license_dir"
