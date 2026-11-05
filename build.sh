#!/system/bin/sh
# 记不住 App 构建脚本：aapt2(termux) + javac + d8 + apksigner
set -e
cd "$(dirname "$0")" || exit 1
SDK=../sdk
AJAR=$SDK/android-34-ext10/android.jar
BT=$SDK/android-14
OUT=build
rm -rf $OUT/gen $OUT/classes $OUT/dex $OUT/app-unsigned.apk
mkdir -p $OUT/gen $OUT/classes $OUT/dex

echo "[1/5] 资源"
RES_ARGS=""
if [ -d res ]; then
  rm -f $OUT/res.zip
  aapt2 compile --dir res -o $OUT/res.zip
  RES_ARGS="-R $OUT/res.zip"
fi

echo "[2/5] link"
aapt2 link -o $OUT/app-unsigned.apk \
  -I "$AJAR" \
  --manifest AndroidManifest.xml \
  --java $OUT/gen \
  --min-sdk-version 26 --target-sdk-version 33 \
  --auto-add-overlay $RES_ARGS

echo "[3/5] javac"
find src $OUT/gen -name '*.java' > $OUT/sources.txt
if ! javac -encoding UTF-8 -source 8 -target 8 -nowarn -bootclasspath "$AJAR" \
  -d $OUT/classes @$OUT/sources.txt > $OUT/javac.log 2>&1; then
  grep -v "bootstrap class path" $OUT/javac.log | head -40
  echo "编译失败"
  exit 1
fi

echo "[4/5] dex"
java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --lib "$AJAR" --min-api 26 --release \
  --output $OUT/dex $(find $OUT/classes -name '*.class')
(cd $OUT/dex && zip -q ../app-unsigned.apk classes.dex)

echo "[5/5] 对齐与签名"
zipalign -f 4 $OUT/app-unsigned.apk $OUT/app-aligned.apk
java -jar $BT/lib/apksigner.jar sign \
  --ks build/ksig --ks-pass pass:123456 --ks-key-alias app \
  --min-sdk-version 26 --v1-signing-enabled true --v2-signing-enabled true \
  --out $OUT/app-signed.apk $OUT/app-aligned.apk
java -jar $BT/lib/apksigner.jar verify --print-certs $OUT/app-signed.apk | head -3
ls -la $OUT/app-signed.apk
