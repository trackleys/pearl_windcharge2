package com.pearlcatch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class PearlCatchClient implements ClientModInitializer {

    private static KeyBinding catchKey;

        private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("pearlcatch", "main"));

    // -1 = ничего не делаем, 0 = кинуть перл, 1 = кинуть винчардж, 2 = вернуть слот
    private int stage = -1;
    private int returnSlot = 0;

    @Override
    public void onInitializeClient() {
        catchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pearlcatch.catch",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,              // дефолтный бинд — G, меняется в настройках управления
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        while (catchKey.wasPressed()) {
            tryStart(client);
        }
        if (stage < 0) return;

        ClientPlayerEntity player = client.player;
        if (player == null) { stage = -1; return; }

        switch (stage) {
            case 0 -> {
                int pearlSlot = findSlot(player, Items.ENDER_PEARL);
                if (pearlSlot < 0) {
                    actionbar(player, "§cНет эндер-жемчуга в хотбаре");
                    stage = -1;
                    return;
                }
                selectAndUse(client, player, pearlSlot);
                stage = 1; // винчардж выполнится в этом же тике — пакеты идут по порядку
            }
            case 1 -> {
                int windSlot = findSlot(player, Items.WIND_CHARGE);
                if (windSlot < 0) {
                    actionbar(player, "§cНет ветряного заряда в хотбаре");
                    stage = 2;
                    return;
                }
                selectAndUse(client, player, windSlot);
                stage = 2; // слот вернётся на следующем тике
            }
            case 2 -> {
                player.getInventory().setSelectedSlot(returnSlot);
                player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(returnSlot));
                stage = -1;
            }
        }
    }

    private void tryStart(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        if (client.currentScreen != null) return;   // не срабатываем в инвентаре/меню
        if (player.isSpectator()) return;
        if (player.isUsingItem()) return;           // не рвём зажатие ЛКМ/еду/лук
        if (stage >= 0) return;                     // последовательность уже идёт
        returnSlot = player.getInventory().getSelectedSlot();
        stage = 0;
    }

    private static int findSlot(ClientPlayerEntity player, Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    // Надёжный способ: сами ставим слот, сами шлём пакет, сами вызываем использование.
    // Никаких имитаций клавиш — порядок пакетов гарантирован TCP.
    private static void selectAndUse(MinecraftClient client, ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
    }

    private static void actionbar(ClientPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), true);
    }
}
