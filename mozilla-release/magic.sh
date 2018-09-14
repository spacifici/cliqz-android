#!/bin/bash

export CLIQZ_CHANNEL=MA50
signer="../../../opt/android-sdk/build-tools/27.0.3/apksigner"
keystore="../../../Dropbox/cliqz/androidkey.jks"
distrib="../distribution/assets/distribution/extensions"
set -x
set -e
DST=../dist
rm -rf "${DST}"
find "${distrib}" -iname "*.xpi" -exec rm -v '{}' ';'
mkdir -p "${DST}"
mkdir -p "${distrib}"

cat "config/privacy.txt"|xargs wget -O "${distrib}/firefox@ghostery.com.xpi"
cat "config/search.txt"|grep "ghostery"|cut -d= -f2|xargs wget -O "${distrib}/android@cliqz.com.xpi"

for arch in arm x86
do
    objdir="../objdir-android-${arch}"
    export MOZCONFIG="ghostery-${arch}.mozconfig"
    # ./mach clobber
    ./mach build
    for lang in de fr ru
    do
        ./mach build chrome-${lang}
    done
    AB_CD=multi ./mach package
    ./gradlew assembleOfficialWithGeckoBinariesNoMinApiPhotonRelease
    unsigned="${DST}/ghostery-${arch}-unsigned.apk"
    signed="${DST}/ghostery-${arch}.apk"
    cp "${objdir}/gradle/build/mobile/android/app/outputs/apk/officialWithGeckoBinariesNoMinApiPhoton/release/app-official-withGeckoBinaries-noMinApi-photon-release-unsigned.apk" "${unsigned}"
    $signer sign --ks "${keystore}" --ks-pass "pass:cliqz-245" --ks-key-alias "ghostery" --in "${unsigned}" --out "${signed}"
    unset MOZCONFIG
done
