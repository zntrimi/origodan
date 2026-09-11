/*
 * Copyright 2026 Mirror GODAN contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

#include <jni.h>

#include <algorithm>
#include <memory>
#include <string>
#include <vector>

#include "defines.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest_options.h"
#include "utils/int_array_view.h"

namespace {

using latinime::CodePointArrayView;
using latinime::DicTraverseSession;
using latinime::Dictionary;
using latinime::DictionaryStructureWithBufferPolicy;
using latinime::DictionaryStructureWithBufferPolicyFactory;
using latinime::NgramContext;
using latinime::ProximityInfo;
using latinime::SuggestOptions;
using latinime::SuggestionResults;

template <typename T>
std::vector<T> copyIntArray(JNIEnv *env, jintArray input, int requestedSize = -1) {
    if (!input) return {};
    const int arraySize = env->GetArrayLength(input);
    const int size = requestedSize < 0 ? arraySize : std::min(arraySize, requestedSize);
    std::vector<jint> temporary(size);
    if (size > 0) env->GetIntArrayRegion(input, 0, size, temporary.data());
    return std::vector<T>(temporary.begin(), temporary.end());
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_imaizentarou_latinime_LatinImeNative_openDictionary(
        JNIEnv *env, jobject, jstring path, jlong size) {
    if (!path || size <= 0 || size > INT32_MAX) return 0;
    const char *utfPath = env->GetStringUTFChars(path, nullptr);
    if (!utfPath) return 0;
    auto policy = DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
            utfPath, 0, static_cast<int>(size), false);
    env->ReleaseStringUTFChars(path, utfPath);
    if (!policy) return 0;
    return reinterpret_cast<jlong>(new Dictionary(env, std::move(policy)));
}

extern "C" JNIEXPORT void JNICALL
Java_dev_imaizentarou_latinime_LatinImeNative_closeDictionary(
        JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Dictionary *>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_imaizentarou_latinime_LatinImeNative_createProximityInfo(
        JNIEnv *env, jobject, jint keyboardWidth, jint keyboardHeight,
        jint gridWidth, jint gridHeight, jint commonKeyWidth, jint commonKeyHeight,
        jintArray proximityChars, jintArray keyXs, jintArray keyYs,
        jintArray keyWidths, jintArray keyHeights, jintArray keyCodes) {
    if (!proximityChars || !keyCodes || keyboardWidth <= 0 || keyboardHeight <= 0) return 0;
    const int keyCount = env->GetArrayLength(keyCodes);
    auto *proximity = new ProximityInfo(
            env, keyboardWidth, keyboardHeight, gridWidth, gridHeight,
            commonKeyWidth, commonKeyHeight, proximityChars, keyCount,
            keyXs, keyYs, keyWidths, keyHeights, keyCodes,
            nullptr, nullptr, nullptr);
    return reinterpret_cast<jlong>(proximity);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_imaizentarou_latinime_LatinImeNative_releaseProximityInfo(
        JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<ProximityInfo *>(handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_imaizentarou_latinime_LatinImeNative_getProbability(
        JNIEnv *env, jobject, jlong dictionaryHandle, jintArray word) {
    auto *dictionary = reinterpret_cast<Dictionary *>(dictionaryHandle);
    if (!dictionary || !word) return NOT_A_PROBABILITY;
    const auto codePoints = copyIntArray<int>(env, word, MAX_WORD_LENGTH);
    return dictionary->getMaxProbabilityOfExactMatches(
            CodePointArrayView(codePoints.data(), codePoints.size()));
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_imaizentarou_latinime_LatinImeNative_getSuggestions(
        JNIEnv *env, jobject, jlong dictionaryHandle, jlong proximityHandle,
        jintArray inputArray, jintArray xArray, jintArray yArray, jintArray timeArray,
        jintArray previousWordArray, jintArray outputCodePoints, jintArray outputScores,
        jintArray outputTypes) {
    auto *dictionary = reinterpret_cast<Dictionary *>(dictionaryHandle);
    auto *proximity = reinterpret_cast<ProximityInfo *>(proximityHandle);
    if (!dictionary || !proximity || !inputArray || !outputCodePoints ||
            !outputScores || !outputTypes) {
        return 0;
    }

    auto input = copyIntArray<int>(env, inputArray, MAX_WORD_LENGTH - 1);
    if (input.empty()) return 0;
    auto xs = copyIntArray<int>(env, xArray, input.size());
    auto ys = copyIntArray<int>(env, yArray, input.size());
    auto times = copyIntArray<int>(env, timeArray, input.size());
    xs.resize(input.size(), -1);
    ys.resize(input.size(), -1);
    times.resize(input.size(), 0);
    std::vector<int> pointerIds(input.size(), 0);

    auto previousWord = copyIntArray<int>(env, previousWordArray, MAX_WORD_LENGTH - 1);
    const NgramContext context = previousWord.empty()
            ? NgramContext()
            : NgramContext(previousWord.data(), previousWord.size(), false);
    const int optionValues[] = {
            0,     // typing, not gesture
            1,     // full edit distance
            1,     // block potentially offensive words
            0,     // space-aware gesture is irrelevant for typing
            1000,  // locale weight 1.0
    };
    const SuggestOptions options(optionValues, sizeof(optionValues) / sizeof(optionValues[0]));
    DicTraverseSession session(env, nullptr, true);
    SuggestionResults results(MAX_RESULTS);
    dictionary->getSuggestions(
            proximity, &session, xs.data(), ys.data(), times.data(), pointerIds.data(),
            input.data(), input.size(), &context, &options,
            NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL, &results);

    jintArray countArray = env->NewIntArray(1);
    jintArray spaceIndices = env->NewIntArray(MAX_RESULTS);
    jintArray autoCommitConfidence = env->NewIntArray(1);
    jfloatArray modelWeight = env->NewFloatArray(1);
    results.outputSuggestions(env, countArray, outputCodePoints, outputScores,
            spaceIndices, outputTypes, autoCommitConfidence, modelWeight);
    jint count = 0;
    env->GetIntArrayRegion(countArray, 0, 1, &count);
    env->DeleteLocalRef(countArray);
    env->DeleteLocalRef(spaceIndices);
    env->DeleteLocalRef(autoCommitConfidence);
    env->DeleteLocalRef(modelWeight);
    return count;
}
