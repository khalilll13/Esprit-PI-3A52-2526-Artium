package utils;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

public class CardAnimator {

    private static final double BASE_DELAY_MS  = 60;
    private static final double DURATION_MS    = 360;
    private static final double SLIDE_OFFSET_Y = 24;
    private static final double SLIDE_OFFSET_X = 16;

    // Fade + glissement vers le haut (cartes, items de liste)
    public static void animateFadeSlideUp(Iterable<? extends Node> nodes, double baseDelayMs) {
        int i = 0;
        for (Node node : nodes) {
            animateFadeSlideUp(node, baseDelayMs + i * BASE_DELAY_MS);
            i++;
        }
    }

    public static void animateFadeSlideUp(Node node, double delayMs) {
        node.setOpacity(0);
        node.setTranslateY(SLIDE_OFFSET_Y);

        FadeTransition fade = new FadeTransition(Duration.millis(DURATION_MS), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(DURATION_MS), node);
        slide.setFromY(SLIDE_OFFSET_Y);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition anim = new ParallelTransition(fade, slide);
        anim.setDelay(Duration.millis(delayMs));
        anim.play();
    }

    // Scale pop élastique (stat cards dashboard)
    public static void animateScalePop(Iterable<? extends Node> nodes, double baseDelayMs) {
        int i = 0;
        for (Node node : nodes) {
            animateScalePop(node, baseDelayMs + i * BASE_DELAY_MS);
            i++;
        }
    }

    public static void animateScalePop(Node node, double delayMs) {
        node.setOpacity(0);
        node.setScaleX(0.87);
        node.setScaleY(0.87);

        FadeTransition fade = new FadeTransition(Duration.millis(DURATION_MS), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(DURATION_MS + 60), node);
        scale.setFromX(0.87);
        scale.setFromY(0.87);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(new ElasticOut());

        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setDelay(Duration.millis(delayMs));
        anim.play();
    }

    // Stagger latéral (items de listes récentes)
    public static void animateStaggerList(Iterable<? extends Node> nodes, double baseDelayMs) {
        int i = 0;
        for (Node node : nodes) {
            node.setOpacity(0);
            node.setTranslateX(-SLIDE_OFFSET_X);

            FadeTransition fade = new FadeTransition(Duration.millis(260), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition slide = new TranslateTransition(Duration.millis(260), node);
            slide.setFromX(-SLIDE_OFFSET_X);
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition anim = new ParallelTransition(fade, slide);
            anim.setDelay(Duration.millis(baseDelayMs + i * 50));
            anim.play();
            i++;
        }
    }

    // Transition page (fade out → fade in + slide)
    public static void animatePageTransition(Node newPage) {
        newPage.setOpacity(0);
        newPage.setTranslateY(14);

        FadeTransition fade = new FadeTransition(Duration.millis(280), newPage);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(280), newPage);
        slide.setFromY(14);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, slide).play();
    }

    // Interpolateur élastique léger
    private static class ElasticOut extends Interpolator {
        @Override
        protected double curve(double t) {
            return 1 - Math.pow(2, -8 * t) * Math.cos(t * Math.PI * 2.2);
        }
    }
}
