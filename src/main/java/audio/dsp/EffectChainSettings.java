package audio.dsp;

/**
 * All effect and dynamics parameters for preview and offline render.
 */
public final class EffectChainSettings implements Cloneable {

    // EQ
    public boolean eqEnabled = true;
    public double lowGainDb = 0;
    public double midGainDb = 0;
    public double highGainDb = 0;
    public double param1Freq = 120;
    public double param1GainDb = 0;
    public double param1Q = 1.0;
    public double param2Freq = 2500;
    public double param2GainDb = 0;
    public double param2Q = 1.0;
    public String eqPreset = "flat";

    // Dynamics
    public boolean compressorEnabled;
    public double compressorThresholdDb = -18;
    public double compressorRatio = 4;
    public double compressorAttackMs = 10;
    public double compressorReleaseMs = 100;
    public boolean limiterEnabled;
    public double limiterCeilingDb = -0.5;
    public boolean gateEnabled;
    public double gateThresholdDb = -40;
    public boolean expanderEnabled;
    public double expanderRatio = 2;
    public boolean deEsserEnabled;
    public double deEsserFreq = 6000;
    public double deEsserAmount = 0.5;

    // Time / pitch
    public double pitchSemitones = 0;
    public double speed = 1.0;
    public double timeStretch = 1.0;

    // Effects
    public boolean reverbEnabled;
    public double reverbRoom = 0.4;
    public double reverbMix = 0.25;
    public boolean delayEnabled;
    public double delayMs = 250;
    public double delayFeedback = 0.35;
    public double delayMix = 0.3;
    public boolean echoEnabled;
    public double echoDelayMs = 400;
    public boolean chorusEnabled;
    public double chorusDepth = 0.3;
    public boolean flangerEnabled;
    public double flangerRate = 0.5;
    public boolean distortionEnabled;
    public double distortionDrive = 0.2;
    public boolean widenerEnabled;
    public double widenerAmount = 0.5;

    // Enhancement
    public boolean noiseReductionEnabled;
    public double noiseReductionAmount = 0.5;
    public boolean humRemovalEnabled;
    public boolean clickRemovalEnabled;
    public boolean breathReductionEnabled;
    public boolean voiceEnhanceEnabled;
    public boolean vocalClarityEnabled;
    public boolean restorationEnabled;

    public double pan = 0;
    public double balance = 0;

    @Override
    public EffectChainSettings clone() {
        try {
            return (EffectChainSettings) super.clone();
        } catch (CloneNotSupportedException e) {
            return new EffectChainSettings();
        }
    }
}
