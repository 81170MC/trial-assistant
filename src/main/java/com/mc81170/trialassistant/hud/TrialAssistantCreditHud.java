
package com.mc81170.trialassistant.hud;

import com.mc81170.trialassistant.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class TrialAssistantCreditHud extends HudElement {

    public static final HudElementInfo<TrialAssistantCreditHud> INFO =
        new HudElementInfo<>(
            AddonTemplate.HUD_GROUP,
            "trial-assistant-credit",
            "Displays the Trial Assistant credit.",
            TrialAssistantCreditHud::new
        );

    public TrialAssistantCreditHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        String credit = "Trial Assistant by 81170";

        setSize(
            renderer.textWidth(credit, true),
            renderer.textHeight(true)
        );

        renderer.text(
            credit,
            x,
            y,
            new Color(170, 170, 170),
            true
        );
    }
}
