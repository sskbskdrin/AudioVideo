#include <jni.h>
#include <Seeta/CFaceInfo.h>
#include "Seeta/Struct.h"
#include "FaceLandmarker/FaceLandmarker.h"

//
// Created by keayuan on 2020/5/18.
//

extern "C"
JNIEXPORT jlong JNICALL
Java_cn_sskbskdrin_lib_face_FaceLandmarker_nativeInitLandmarker(JNIEnv *env, jclass clazz, jstring marker_model_file) {
    const char *markerModelFile = env->GetStringUTFChars(marker_model_file, 0);
    seeta::ModelSetting::Device device = seeta::ModelSetting::AUTO;

    int id = 0;
    seeta::ModelSetting FL_model(markerModelFile, device, id);

    seeta::FaceLandmarker *FL = new seeta::FaceLandmarker(FL_model);

    env->ReleaseStringUTFChars(marker_model_file, markerModelFile);
    return (jlong) FL;
}

extern "C"
JNIEXPORT jint JNICALL
Java_cn_sskbskdrin_lib_face_FaceLandmarker_nativeMark(JNIEnv *env, jclass clazz, jlong id, jbyteArray src_, jint width,
                                                      jint height, jdoubleArray dest_points, jintArray rect_,
                                                      jint face_count) {
    if (!id) {
        return 0;
    }
    seeta::FaceLandmarker *engine = (seeta::FaceLandmarker *) id;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_, NULL));
    jdouble *points = env->GetDoubleArrayElements(dest_points, NULL);
    int *rect = env->GetIntArrayElements(rect_, NULL);

    seeta::ImageData image(width, height, 3);
    image.data = src;

    SeetaRect faceRect;
    int pointIndex = 0;
    for (int i = 0; i < face_count; ++i) {
        faceRect.x = rect[i * 4];
        faceRect.y = rect[i * 4 + 1];
        faceRect.width = rect[i * 4 + 2];
        faceRect.height = rect[i * 4 + 3];
        std::vector<SeetaPointF> temp = engine->mark(image, faceRect);
        for (int j = 0; j < temp.size(); ++j) {
            points[pointIndex++] = temp[j].x;
            points[pointIndex++] = temp[j].y;
        }
    }
    env->ReleaseByteArrayElements(src_, (jbyte *) src, 0);
    env->ReleaseIntArrayElements(rect_, rect, 0);
    env->ReleaseDoubleArrayElements(dest_points, points, 0);
    return face_count;
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_face_FaceLandmarker_nativeReleaseLandmarker(JNIEnv *env, jclass clazz, jlong id) {
    if (id) {
        delete (seeta::FaceLandmarker *) id;
    }
}