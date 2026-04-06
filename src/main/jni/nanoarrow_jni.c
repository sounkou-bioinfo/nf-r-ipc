#if __has_include(<jni.h>)
#include <jni.h>
#define NFR_HAS_JNI 1
#else
#include <stddef.h>
typedef void JNIEnv;
typedef void* jobject;
typedef void* jclass;
typedef void* jstring;
#ifndef JNIEXPORT
#define JNIEXPORT
#endif
#ifndef JNICALL
#define JNICALL
#endif
#define NFR_HAS_JNI 0
#endif

JNIEXPORT void JNICALL
Java_nextflow_nfr_codec_jni_NanoarrowJniNative_writeRequest(
    JNIEnv* env,
    jclass cls,
    jstring ipcPath,
    jobject control,
    jobject data
) {
    (void)env;
    (void)cls;
    (void)ipcPath;
    (void)control;
    (void)data;
}

JNIEXPORT jobject JNICALL
Java_nextflow_nfr_codec_jni_NanoarrowJniNative_readResponse(
    JNIEnv* env,
    jclass cls,
    jstring ipcPath
) {
#if !NFR_HAS_JNI
    (void)env;
    (void)cls;
    (void)ipcPath;
    return NULL;
#else
    (void)cls;
    (void)ipcPath;

    jclass mapClass = (*env)->FindClass(env, "java/util/LinkedHashMap");
    if (mapClass == NULL) {
        return NULL;
    }

    jmethodID ctor = (*env)->GetMethodID(env, mapClass, "<init>", "()V");
    if (ctor == NULL) {
        return NULL;
    }

    jobject topMap = (*env)->NewObject(env, mapClass, ctor);
    if (topMap == NULL) {
        return NULL;
    }

    jmethodID put = (*env)->GetMethodID(
        env,
        mapClass,
        "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
    );
    if (put == NULL) {
        return NULL;
    }

    jobject controlMap = (*env)->NewObject(env, mapClass, ctor);
    if (controlMap == NULL) {
        return NULL;
    }

    jstring keyStatus = (*env)->NewStringUTF(env, "status");
    jstring valueStatus = (*env)->NewStringUTF(env, "ok");
    jstring keyControl = (*env)->NewStringUTF(env, "control");
    jstring keyData = (*env)->NewStringUTF(env, "data");

    (*env)->CallObjectMethod(env, controlMap, put, keyStatus, valueStatus);
    (*env)->CallObjectMethod(env, topMap, put, keyControl, controlMap);
    (*env)->CallObjectMethod(env, topMap, put, keyData, NULL);

    return topMap;
#endif
}
