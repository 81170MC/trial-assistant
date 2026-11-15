package com.mc81170.trialassistant;

import com.mc81170.trialassistant.modules.TrialChambersAssistant;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.XAnchor;
import meteordevelopment.meteorclient.systems.hud.YAnchor;
import meteordevelopment.meteorclient.utils.PostInit;
import com.mc81170.trialassistant.hud.TrialAssistantCreditHud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Trial Assistant");
    public static final HudGroup HUD_GROUP = new HudGroup("Trial Assistant HUD");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Addon Template");

        Modules.get().add(new TrialChambersAssistant());
        Hud.get().register(TrialAssistantCreditHud.INFO);


    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.mc81170.trialassistant";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("81170", "trial-assistant");
    }

    @PostInit
    public static void addCreditToHud() {
        Hud hud = Hud.get();

        boolean alreadyAdded = false;

        for (var element : hud) {
            if (element.getClass() == TrialAssistantCreditHud.class) {
                alreadyAdded = true;
                break;
            }
        }

        if (!alreadyAdded) {
            hud.add(
                TrialAssistantCreditHud.INFO,
                4,
                -4,
                XAnchor.Left,
                YAnchor.Bottom
            );
        }
    }

}
