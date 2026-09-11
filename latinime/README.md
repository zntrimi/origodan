# Embedded AOSP LatinIME suggestion core

This module embeds the native typing suggestion and dictionary runtime from AOSP LatinIME at
commit `127336e9f29d69607eab55982324b210279ae8c5` (2025-02-26). The upstream source and its
modifications are licensed under Apache License 2.0; see `LICENSE-Apache-2.0`, `NOTICE-AOSP`, and
the copyright headers in `src/main/cpp/aosp`.

`main_en_US.dict` is the compiled form of AOSP's `en_US_wordlist.combined.gz`. The corresponding
source word list is retained in `upstream-data` for attribution and reproducibility, but is not
packaged into the APK.

Only the engine is used. Mirror GODAN keeps its existing input method service, layouts and candidate
UI, and calls this module through `LatinImeEngine`.
