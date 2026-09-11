# Origodan (Mirror GODAN)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.10-7F52FF)

折りたたみ端末と大画面Android向けに、左右どちらの手でも同じGODAN配列を使えるようにした日本語キーボードです。

このリポジトリは [Sumire](https://github.com/KazumaProject/JapaneseKeyboard) の非公式フォークです。Android上では現在「Mirror GODAN」と表示されます。

> [!IMPORTANT]
> 現在は開発中のプレビュー版です。Google Playなどでの公式配布はまだ行っていません。日常利用する場合も、更新前に辞書や設定をバックアップしてください。

## 特徴

### 折りたたみ端末向けMirror GODAN

- 600dp以上の大画面では、左右に同じ3列のGODAN文字盤を表示
- 操作キーは左右の外側へ配置し、ヒンジ付近には置かない設計
- 中央の空間に、スクロールできるクリップボード履歴を表示
- カバー画面や通常幅では、片側5列のコンパクト表示
- フリック候補をキー上に常時表示
- 最下段を含むタッチ判定、振動、キーサイズを大画面向けに調整

### 日本語入力

- GODANローマ字入力
- Mozc由来の辞書を使った端末内かな漢字変換
- ユーザー辞書、定型文、絵文字・記号候補
- 確定履歴に基づく候補の並べ替え
- 次に入力されやすい語句の個人学習
- 「n」の後に母音が続くケースを含む、GODAN向けローマ字合成
- Full版ではZenzによる再ランキングを任意で利用可能

### 英語入力

- 数字列付きQWERTYレイアウト
- 長押し記号とキー近接情報
- AOSP LatinIMEの辞書・候補生成コアを利用したタイポ補正
- Damerau–Levenshtein距離、キー位置、単語頻度を組み合わせた候補順位
- 文脈に応じた自動修正と、学習した語句の優先表示

### ツール

- 複数件のクリップボード履歴
- Androidの音声認識を使った音声入力
- 絵文字・記号パネル
- カーソル移動、言語切替、入力モード切替
- 端末幅に応じて変化するツールバー

## 音声入力について

Android 12以降では、利用可能ならオンデバイス音声認識を優先します。選択した言語がオンデバイス認識に対応していない場合は、端末の標準音声認識へフォールバックします。

標準音声認識がネットワークを使うかどうかは、端末メーカー、選択中の音声認識サービス、言語データのインストール状況によって異なります。

## プライバシー

- アプリ自身は `INTERNET` 権限を要求しません
- 日本語変換、英語候補、学習処理はアプリ内で実行します
- 学習データとクリップボード履歴は端末内に保存します
- 音声入力を使う場合だけ `RECORD_AUDIO` 権限を要求します
- 音声データの処理方法は、Androidが選択した音声認識サービスにも依存します

キーボードは入力内容へアクセスできる強い権限を持つアプリです。公開APKを利用するときは、配布元と署名を確認してください。

## 対応環境

| 項目 | 内容 |
|:--|:--|
| Android | 7.0（API 24）以上 |
| compileSdk / targetSdk | 36 |
| JDK | 17 |
| Android Gradle Plugin | 8.10.1 |
| NDK | 29.0.14206865 |
| 主な対象 | Galaxy Z Foldなどの折りたたみ端末、タブレット、通常のAndroid端末 |

ネイティブの英語補正エンジンをビルドするため、Android SDKに加えてNDKとCMakeが必要です。

## ソースからビルド

### 1. クローン

```shell
git clone git@github.com:zntrimi/origodan.git
cd origodan
```

HTTPSを使う場合:

```shell
git clone https://github.com/zntrimi/origodan.git
cd origodan
```

### 2. Liteデバッグ版を作成

```shell
./gradlew :app:assembleLiteStandardDebug --no-daemon --max-workers=1
```

APK:

```text
app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
```

接続済み端末へインストール:

```shell
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
```

### 3. キーボードを有効化

1. Androidの「設定」からキーボード管理画面を開く
2. 「Mirror GODAN」を有効にする
3. 入力欄のキーボード切替ボタンから「Mirror GODAN」を選ぶ

## ビルドバリアント

| バリアント | 内容 |
|:--|:--|
| `liteStandard` | Zenz/Gemmaを含まない推奨開発版 |
| `fullStandard` | Zenz/Gemmaを含む実験的な全部入り版 |
| `liteFdroid` | F-Droid向け軽量版 |

`fullFdroid` は無効です。Full版はビルド時にZenzモデルを取得するため、初回ビルドにPython 3とネットワーク接続が必要です。モデルはAPKへ組み込まれ、実行時にアプリがダウンロードする設計ではありません。

署名なしRelease APK:

```shell
./gradlew :app:assembleLiteStandardReleaseUnsigned --no-daemon --max-workers=1
```

## テスト

主なJVMテスト:

```shell
./gradlew \
  :app:testLiteStandardDebugUnitTest \
  :custom_keyboard:testDebugUnitTest \
  :qwerty_keyboard:testDebugUnitTest \
  --no-daemon \
  --max-workers=1
```

接続端末でのテスト:

```shell
./gradlew :app:connectedLiteStandardDebugAndroidTest --no-daemon --max-workers=1
```

## 主なモジュール

| モジュール | 役割 |
|:--|:--|
| `app` | IMEサービス、変換、学習、設定、音声入力、ツールバー |
| `custom_keyboard` | Mirror GODANレイアウト、フリック、ヒットテスト |
| `qwerty_keyboard` | 英語QWERTY表示とタッチ入力 |
| `latinime` | AOSP LatinIME由来の英語候補生成エンジン |
| `symbol_keyboard` | 絵文字・記号・クリップボード画面 |
| `zenz` | 任意のニューラル日本語変換再ランキング |

## 開発への参加

不具合報告や機能提案は [Issues](https://github.com/zntrimi/origodan/issues)、変更提案はPull Requestで受け付けます。

報告には次の情報があると調査しやすくなります。

- 端末名、Androidバージョン、開いた状態か閉じた状態か
- 使用した入力モード
- 再現手順
- 個人情報を除いたスクリーンショットまたは画面録画

## 派生元とライセンス

Origodanは [KazumaProject/JapaneseKeyboard (Sumire)](https://github.com/KazumaProject/JapaneseKeyboard) を基にしています。プロジェクト本体とOrigodanの変更部分は [MIT License](LICENSE) で公開します。

主な第三者コンポーネント:

- [Mozc](https://github.com/google/mozc) — BSD 3-Clause
- [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/) — Apache License 2.0

  使用したrevisionと変更範囲は [latinime/README.md](latinime/README.md)、ライセンスとNOTICEは [latinime/LICENSE-Apache-2.0](latinime/LICENSE-Apache-2.0) および [latinime/NOTICE-AOSP](latinime/NOTICE-AOSP) を参照してください。
- Mozc UT系辞書、Wikipedia由来データ、mecab-ipadic-neologd、Zenzモデルなどは、それぞれのライセンスに従います
- その他の依存ライブラリは、アプリ内の「オープンソースライセンス」と各ソースファイルの表記を参照してください

---

## English

Origodan is an experimental, privacy-conscious Android keyboard for foldables and large displays. It mirrors the GODAN letter block on both sides of an unfolded screen, provides local Japanese conversion and personalization, and embeds the AOSP LatinIME suggestion core for English typo correction.

The app does not request the `INTERNET` permission. Voice input prefers Android's on-device recognizer when available and otherwise uses the system speech recognizer, whose network behavior depends on the device and provider.

Origodan is an unofficial fork of [Sumire](https://github.com/KazumaProject/JapaneseKeyboard). See the build and license sections above for details.
