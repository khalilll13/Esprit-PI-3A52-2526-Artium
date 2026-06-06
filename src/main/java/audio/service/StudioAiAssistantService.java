package audio.service;

import audio.dsp.AudioAnalysis;
import audio.dsp.AudioEffects;
import audio.dsp.EffectChainSettings;
import audio.model.AudioBuffer;
import audio.model.StudioProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Heuristic + analysis-driven "AI" tools (no external API required).
 */
public final class StudioAiAssistantService {

    public record AiSuggestion(String title, String detail, Runnable apply) {
    }

    public List<AiSuggestion> analyzeAndSuggest(StudioProject project) {
        List<AiSuggestion> list = new ArrayList<>();
        AudioBuffer buf = project.getMasterBuffer();
        AudioAnalysis.AnalysisReport r = AudioAnalysis.analyze(buf);
        EffectChainSettings fx = project.getMasterEffects();

        list.add(new AiSuggestion(
                "Réduction de bruit automatique",
                "Atténue les passages sous le seuil de bruit.",
                () -> {
                    fx.noiseReductionEnabled = true;
                    fx.noiseReductionAmount = 0.65;
                }));

        if (r.rmsDb < -24) {
            list.add(new AiSuggestion(
                    "Améliorer le volume vocal",
                    String.format(Locale.ROOT, "Niveau RMS faible (%.1f dB). Compression + EQ vocal.", r.rmsDb),
                    () -> {
                        fx.voiceEnhanceEnabled = true;
                        fx.compressorEnabled = true;
                        fx.compressorThresholdDb = -22;
                        AudioEffects.applyEqPreset(fx, "vocal");
                    }));
        }

        if (!r.silentRegions.isEmpty()) {
            list.add(new AiSuggestion(
                    "Sections silencieuses détectées",
                    r.silentRegions.size() + " zone(s) — envisagez un trim.",
                    () -> {
                    }));
        }

        if (r.clippingDetected) {
            list.add(new AiSuggestion(
                    "Écrêtage détecté",
                    r.clipCount + " échantillons au plafond — limiteur recommandé.",
                    () -> {
                        fx.limiterEnabled = true;
                        fx.limiterCeilingDb = -1.0;
                        project.getMastering().limiterEnabled = true;
                    }));
        }

        list.add(new AiSuggestion(
                "Mastering streaming suggéré",
                "Cible ~ -14 LUFS avec limiteur.",
                () -> {
                    project.getMastering().preset = "streaming";
                    project.getMastering().normalize = true;
                    project.getMastering().targetLufs = -14;
                }));

        list.add(new AiSuggestion(
                "Nettoyage audio en un clic",
                "Denoise + de-click + EQ podcast.",
                () -> {
                    fx.noiseReductionEnabled = true;
                    fx.clickRemovalEnabled = true;
                    fx.humRemovalEnabled = true;
                    AudioEffects.applyEqPreset(fx, "podcast");
                    fx.compressorEnabled = true;
                }));

        return list;
    }

    public AudioBuffer autoEnhance(AudioBuffer input) {
        EffectChainSettings fx = new EffectChainSettings();
        fx.noiseReductionEnabled = true;
        fx.noiseReductionAmount = 0.55;
        fx.clickRemovalEnabled = true;
        fx.voiceEnhanceEnabled = true;
        fx.compressorEnabled = true;
        fx.limiterEnabled = true;
        AudioEffects.applyEqPreset(fx, "music");
        return AudioEffects.process(input, fx);
    }
}
