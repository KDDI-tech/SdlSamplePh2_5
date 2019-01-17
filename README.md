# SdlSamplePh2_5

### 本アプリケーションの位置づけ
[SdlSamplePh2](https://github.com/KDDI-tech?tab=repositories)で用いた
[sdl_android](https://github.com/smartdevicelink/sdl_android)を
[4.6.3](https://github.com/smartdevicelink/sdl_android/tree/4.6.3)から
[4.7.1](https://github.com/smartdevicelink/sdl_android/tree/4.7.1)に切り替えたアプリケーションになります。   
本ドキュメントの内容は、2018/11/16時点のものになります。

### アプリケーションで利用しているsdl_androidのバージョン
+ [sdl_android(4.7.1)](https://github.com/smartdevicelink/sdl_android/tree/4.7.1)

### ドキュメント
+ [開発ガイド(4.7.1)](https://github.com/smartdevicelink/sdl_android_guides/tree/4.7.1)

### 注意事項
4.6.3から4.7.0へのアップデートには、非常に大きな変更が加えられています。   
詳細については[リリースノート](https://github.com/smartdevicelink/sdl_android/releases)をご確認ください。

### 4.7系へアップデートしたことによる既知の問題
+ ロックスクリーンが表示されない。   
  ロックスクリーンは4.7系へのアップデート時に、大きく実装方法が変わった機能の一つです。   
  [更新ガイド](https://smartdevicelink.com/en/guides/android/migrating-to-newer-sdl-android-versions/updating-to-47/#lock-screen)
  (あるいは[HelloWorld](https://github.com/smartdevicelink/sdl_android/tree/4.7.1/hello_sdl_android))では正常に動作しなかったため、代替手段で実装しています。   
  →HMI-StatusがFullの時にロックスクリーンを表示、Full以外の時にロックスクリーンを落とすように代替実装しています。
+ Manticoreとの接続が切れない。   
  これまでは、Manticoreの画面右上にあるメニューボタンからアプリケーションの終了を出来るようにしていましたが、
  4.7系では正常に接続断が出来なくなっています。

#### 実行環境、制限事項
#### Manticoreの使い方
#### アプリケーションのデプロイ先
#### アプリケーションの起動方法
#### 本アプリケーションでサポートしている動作
#### ソースコード(Javaファイル)について
+ 上記の「実行環境、制限事項」～「ソースコード(Javaファイル)について」については、[SdlSamplePh1 または SdlSamplePh2](https://github.com/KDDI-tech?tab=repositories)をご参照ください。

### ライセンス情報について

##### 本ソフトウェアのライセンスについて
SdlSamplePh2_5 is released under the [Apache 2.0 license](LICENSE).

```
Copyright 2018 KDDI Technology Corp.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

##### 本ソフトウェアで使用しているライセンスについて
```
本ソフトウェアには、様々なオープンソースソフトウェアが含まれています。
各ソフトウェア及びそのライセンス内容に関しては、本ソフトウェア内からご確認いただけますので、内容をご一読くださいますよう、よろしくお願い申し上げます。
```

### Disclaimer

This is not an officially supported KDDI Technology product.
