// Binding JNI — extraction des features fbank pour l'alignement force CTC
// (Tache 5.2). Reprend EXACTEMENT les parametres deja confirmes et
// documentes dans compute_nemo_fbank() (extract_log_probs.py, prototype
// Python valide) et dans le prototype JNI deja prouve sur device reel
// (docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md §6). Aucun parametre
// invente ici.
#include <jni.h>

#include <cmath>
#include <vector>

#include "kaldi-native-fbank/csrc/online-feature.h"

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_inktone_infrastructure_tts_CtcFbankNative_computeNemoFbank(
    JNIEnv *env, jclass /*clazz*/, jfloatArray audio, jint sampleRate) {
  jsize n = env->GetArrayLength(audio);
  std::vector<float> samples(n);
  env->GetFloatArrayRegion(audio, 0, n, samples.data());

  knf::FbankOptions opts;
  opts.frame_opts.samp_freq = sampleRate;
  opts.frame_opts.dither = 0.0f;
  opts.frame_opts.snip_edges = false;
  opts.frame_opts.frame_shift_ms = 10.0f;
  opts.frame_opts.frame_length_ms = 25.0f;
  opts.frame_opts.remove_dc_offset = true;
  opts.frame_opts.preemph_coeff = 0.97f;
  opts.frame_opts.window_type = "povey";
  opts.frame_opts.round_to_power_of_two = true;
  opts.mel_opts.num_bins = 80;
  opts.mel_opts.low_freq = 20.0f;
  opts.mel_opts.high_freq = -400.0f;
  opts.mel_opts.is_librosa = false;

  knf::OnlineFbank fbank(opts);
  fbank.AcceptWaveform(sampleRate, samples.data(), n);
  fbank.InputFinished();

  int32_t num_frames = fbank.NumFramesReady();
  int32_t dim = 80;

  std::vector<std::vector<float>> feats(num_frames, std::vector<float>(dim));
  for (int32_t t = 0; t < num_frames; ++t) {
    const float *frame = fbank.GetFrame(t);
    for (int32_t d = 0; d < dim; ++d) {
      feats[t][d] = frame[d];
    }
  }

  // NemoNormalizePerFeature (math.cc:132 cote sherpa-onnx) : par bin mel
  // (colonne), (x - mean) / (std + 1e-5), variance population (ddof=0).
  std::vector<double> mean(dim, 0.0);
  std::vector<double> var(dim, 0.0);
  for (int32_t d = 0; d < dim; ++d) {
    double sum = 0.0;
    for (int32_t t = 0; t < num_frames; ++t) sum += feats[t][d];
    mean[d] = num_frames > 0 ? sum / num_frames : 0.0;
  }
  for (int32_t d = 0; d < dim; ++d) {
    double sumsq = 0.0;
    for (int32_t t = 0; t < num_frames; ++t) {
      double diff = feats[t][d] - mean[d];
      sumsq += diff * diff;
    }
    var[d] = num_frames > 0 ? sumsq / num_frames : 0.0;
  }

  std::vector<float> out(num_frames * dim);
  for (int32_t t = 0; t < num_frames; ++t) {
    for (int32_t d = 0; d < dim; ++d) {
      double inv_std = 1.0 / (std::sqrt(std::max(var[d], 0.0)) + 1e-5);
      out[t * dim + d] =
          static_cast<float>((feats[t][d] - mean[d]) * inv_std);
    }
  }

  jfloatArray result = env->NewFloatArray(out.size());
  env->SetFloatArrayRegion(result, 0, out.size(), out.data());
  return result;
}
