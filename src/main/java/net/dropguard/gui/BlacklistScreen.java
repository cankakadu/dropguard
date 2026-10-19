package net.dropguard.gui;

import net.dropguard.DropGuardClient;
import net.dropguard.DropGuardConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Dark-Theme-Menü für Item- und Slot-Blacklist. */
public class BlacklistScreen extends Screen {
    private enum Tab { ITEMS, OVERVIEW, SLOTS }

    // Farben (ARGB)
    private static final int BG = 0xFF12141A, PANEL = 0xFF1A1D26, CARD = 0xFF252A38, CARD_HOVER = 0xFF30364A;
    private static final int ACCENT = 0xFF7C5CFF, ACCENT2 = 0xFF00D1B2, LOCK = 0xFFFF4D5E, LOCK_BG = 0xFF3A1C24;
    private static final int TEXT = 0xFFE6E8EF, MUTED = 0xFF8A90A2, GOLD = 0xFFFFC857;

    private record Label(Text text, int x, int y, int color) {}

    private final DropGuardConfig cfg = DropGuardConfig.get();
    private final List<Item> allItems = new ArrayList<>();
    private final Map<Item, String> searchKey = new HashMap<>();

    private Tab tab = Tab.ITEMS;
    private String query = "";
    private int page = 0, pages = 1, listSize = 0;
    private boolean confirming = false;
    private long openedAt = 0;
    private float fade = 0f;

    private int px, py, pw, ph, contentX, contentY, contentW, contentH, footerY;
    private TextFieldWidget search;
    private final List<ClickableWidget> dynamic = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    public BlacklistScreen() {
        super(Text.translatable("dropguard.title"));
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            allItems.add(item);
            searchKey.put(item, (item.getName().getString() + " " + Registries.ITEM.getId(item))
                    .toLowerCase(Locale.ROOT));
        }
    }

    // ------------------------------------------------------------------ Layout

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = Util.getMeasuringTimeMs();
        pw = Math.min(width - 12, 460);
        ph = Math.min(height - 12, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int sideW = Math.min(104, pw / 3);
        contentX = px + sideW + 16;
        contentW = pw - sideW - 24;
        contentY = py + 28;
        contentH = ph - 28 - 8;
        footerY = contentY + contentH - 18;

        dynamic.clear();
        labels.clear();
        search = null;

        if (confirming) {
            int cx = width / 2, cy = height / 2;
            add(new FlatButton(cx - 86, cy + 12, 82, 20, Text.translatable("dropguard.yes"), () -> {
                cfg.resetAll();
                confirming = false;
                page = 0;
                query = "";
                clearAndInit();
            }).danger());
            add(new FlatButton(cx + 4, cy + 12, 82, 20, Text.translatable("dropguard.no"), () -> {
                confirming = false;
                clearAndInit();
            }));
            return;
        }

        // Sidebar
        int sx = px + 8, sw = sideW;
        int y = py + 28;
        for (Tab t : Tab.values()) {
            Text name = Text.translatable("dropguard.tab." + t.name().toLowerCase(Locale.ROOT));
            FlatButton b = new FlatButton(sx, y, sw, 18, name, () -> {
                tab = t;
                page = 0;
                clearAndInit();
            });
            b.selected = () -> tab == t;
            add(b);
            y += 21;
        }
        int by = py + ph - 8 - 3 * 20 - 2 * 3;
        boolean slotsTab = tab == Tab.SLOTS;
        boolean on = slotsTab ? cfg.slotBlacklistEnabled : cfg.itemBlacklistEnabled;
        String toggleKey = "dropguard.toggle." + (slotsTab ? "slots" : "items") + (on ? ".on" : ".off");
        FlatButton toggle = new FlatButton(sx, by, sw, 20, Text.translatable(toggleKey), () -> {
            if (slotsTab) cfg.slotBlacklistEnabled = !cfg.slotBlacklistEnabled;
            else cfg.itemBlacklistEnabled = !cfg.itemBlacklistEnabled;
            cfg.save();
            clearAndInit();
        });
        toggle.accent = on ? ACCENT2 : LOCK;
        add(toggle);
        add(new FlatButton(sx, by + 23, sw, 20,
                Text.translatable(slotsTab ? "dropguard.reset.slots" : "dropguard.reset.items"), () -> {
            if (slotsTab) cfg.clearSlots(); else cfg.clearItems();
            page = 0;
            rebuildDynamic();
        }));
        add(new FlatButton(sx, by + 46, sw, 20, Text.translatable("dropguard.reset.all"), () -> {
            confirming = true;
            clearAndInit();
        }).danger());

        if (!slotsTab) {
            search = new TextFieldWidget(textRenderer, contentX, contentY, contentW, 18, Text.empty());
            search.setPlaceholder(Text.translatable("dropguard.search"));
            search.setText(query);
            search.setChangedListener(s -> {
                query = s;
                page = 0;
                rebuildDynamic();
            });
            addDrawableChild(search);
        }
        buildDynamic();
    }

    private <T extends ClickableWidget> T add(T w) {
        return addDrawableChild(w);
    }

    private void rebuildDynamic() {
        for (ClickableWidget w : dynamic) remove(w);
        dynamic.clear();
        labels.clear();
        buildDynamic();
    }

    private void buildDynamic() {
        if (tab == Tab.SLOTS) buildSlots(); else buildGrid();
    }

    // ------------------------------------------------------------------ Item-Raster

    private List<Item> filtered() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> source;
        if (tab == Tab.OVERVIEW) {
            source = new ArrayList<>();
            for (String id : cfg.items) {
                Identifier ident = Identifier.tryParse(id);
                if (ident != null && Registries.ITEM.containsId(ident)) source.add(Registries.ITEM.get(ident));
            }
        } else {
            source = allItems;
        }
        if (q.isEmpty()) return source;
        List<Item> out = new ArrayList<>();
        for (Item i : source) {
            String key = searchKey.get(i);
            if (key == null) key = Registries.ITEM.getId(i).toString();
            if (key.contains(q)) out.add(i);
        }
        return out;
    }

    private void buildGrid() {
        List<Item> list = filtered();
        listSize = list.size();
        int top = contentY + 22;
        int gridH = footerY - 4 - top;
        int cols = Math.max(1, contentW / 20), rows = Math.max(1, gridH / 20);
        int per = cols * rows;
        pages = Math.max(1, (listSize + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        int gx = contentX + (contentW - cols * 20) / 2;

        for (int i = 0; i < per && page * per + i < listSize; i++) {
            Item item = list.get(page * per + i);
            String id = Registries.ITEM.getId(item).toString();
            int x = gx + (i % cols) * 20, y = top + (i / cols) * 20;
            FlatButton b = new FlatButton(x, y, 18, 18, Text.empty(), () -> {
                cfg.toggleItem(id);
                if (tab == Tab.OVERVIEW) rebuildDynamic();
            });
            ItemStack stack = item.getDefaultStack();
            b.icon = () -> stack;
            b.locked = () -> cfg.hasItem(id);
            b.setTooltip(Tooltip.of(Text.empty().append(item.getName())
                    .append(Text.literal("\n" + id).formatted(Formatting.DARK_GRAY))));
            dynamic.add(add(b));
        }
        dynamic.add(add(new FlatButton(contentX, footerY, 26, 18, Text.literal("<"),
                () -> { page = Math.max(0, page - 1); rebuildDynamic(); })));
        dynamic.add(add(new FlatButton(contentX + contentW - 26, footerY, 26, 18, Text.literal(">"),
                () -> { page = Math.min(pages - 1, page + 1); rebuildDynamic(); })));
    }

    // ------------------------------------------------------------------ Slot-Ansicht

    private void buildSlots() {
        int c = Math.max(14, Math.min(22, (contentH - 52) / 4));
        c = Math.min(c, contentW / 9);
        int gw = 9 * c, gx = contentX + (contentW - gw) / 2;
        int y = contentY + 4;

        Text armor = Text.translatable("dropguard.section.armor");
        Text off = Text.translatable("dropguard.section.offhand");
        labels.add(new Label(armor, gx, y, MUTED));
        labels.add(new Label(off, gx + gw - textRenderer.getWidth(off), y, MUTED));
        y += 11;
        String[] armorNames = {"head", "chest", "legs", "feet"};
        for (int k = 0; k < 4; k++) {
            slotButton(gx + k * c, y, c, 39 - k, Text.translatable("dropguard.slot." + armorNames[k]));
        }
        slotButton(gx + 8 * c, y, c, 40, Text.translatable("dropguard.slot.offhand"));
        y += c + 6;

        labels.add(new Label(Text.translatable("dropguard.section.main"), gx, y, MUTED));
        y += 11;
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 9; col++) {
                int idx = 9 + r * 9 + col;
                slotButton(gx + col * c, y + r * c, c, idx, Text.translatable("dropguard.slot.main", idx - 8));
            }
        }
        y += 3 * c + 6;

        labels.add(new Label(Text.translatable("dropguard.section.hotbar"), gx, y, MUTED));
        y += 11;
        for (int col = 0; col < 9; col++) {
            slotButton(gx + col * c, y, c, col, Text.translatable("dropguard.slot.hotbar", col + 1));
        }
    }

    private void slotButton(int x, int y, int cell, int idx, Text tip) {
        FlatButton b = new FlatButton(x, y, cell - 2, cell - 2, Text.empty(), () -> cfg.toggleSlot(idx));
        b.icon = () -> client != null && client.player != null
                ? client.player.getInventory().getStack(idx) : ItemStack.EMPTY;
        b.locked = () -> cfg.hasSlot(idx);
        b.setTooltip(Tooltip.of(tip));
        dynamic.add(add(b));
    }

    // ------------------------------------------------------------------ Rendering

    private static int a(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static void drawLock(DrawContext c, int x, int y, int col) {
        c.fill(x + 1, y + 3, x + 6, y + 7, col);   // Körper
        c.fill(x + 2, y, x + 5, y + 1, col);       // Bügel oben
        c.fill(x + 2, y + 1, x + 3, y + 3, col);
        c.fill(x + 4, y + 1, x + 5, y + 3, col);
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        float t = Math.min(1f, (Util.getMeasuringTimeMs() - openedAt) / 220f);
        fade = 1f - (float) Math.pow(1f - t, 3);
        float f = Math.max(fade, 0.02f);

        c.fill(0, 0, width, height, a(0xB0000000, f));
        c.fill(px, py, px + pw, py + ph, a(BG, f));
        outline(c, px, py, pw, ph, a(0xFF2C3142, f));
        c.fill(px, py, px + pw, py + 3, a(ACCENT, f));
        c.drawText(textRenderer, Text.translatable("dropguard.title"), px + 10, py + 10, a(TEXT, f), true);
        Text counts = Text.translatable("dropguard.counts", cfg.items.size(), cfg.slots.size());
        c.drawText(textRenderer, counts, px + pw - 10 - textRenderer.getWidth(counts), py + 10, a(MUTED, f), false);

        if (confirming) {
            int cx = width / 2, cy = height / 2;
            c.fill(cx - 100, cy - 38, cx + 100, cy + 40, a(PANEL, f));
            outline(c, cx - 100, cy - 38, 200, 78, a(LOCK, f));
            Text title = Text.translatable("dropguard.confirm.title");
            c.drawText(textRenderer, title, cx - textRenderer.getWidth(title) / 2, cy - 30, a(TEXT, f), true);
            int ty = cy - 16;
            for (var line : textRenderer.wrapLines(Text.translatable("dropguard.confirm.text"), 184)) {
                c.drawText(textRenderer, line, cx - 92, ty, a(MUTED, f), false);
                ty += 10;
            }
            return;
        }

        int sideW = Math.min(104, pw / 3);
        c.fill(px + 6, py + 24, px + 10 + sideW, py + ph - 6, a(PANEL, f));
        c.fill(contentX - 6, py + 24, px + pw - 6, py + ph - 6, a(PANEL, f));

        for (Label l : labels) c.drawText(textRenderer, l.text(), l.x(), l.y(), a(l.color(), f), false);

        if (tab != Tab.SLOTS) {
            Text pg = Text.translatable("dropguard.page", page + 1, pages, listSize);
            c.drawText(textRenderer, pg, contentX + (contentW - textRenderer.getWidth(pg)) / 2, footerY + 5,
                    a(MUTED, f), false);
            if (tab == Tab.OVERVIEW && listSize == 0) {
                Text e = Text.translatable("dropguard.empty");
                c.drawText(textRenderer, e, contentX + (contentW - textRenderer.getWidth(e)) / 2,
                        contentY + contentH / 2 - 4, a(MUTED, f), false);
            }
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean keyPressed(KeyInput input) {
        if (search != null && search.isFocused()) return super.keyPressed(input);
        if (DropGuardClient.OPEN_KEY != null && DropGuardClient.OPEN_KEY.matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ Eigener Button

    private class FlatButton extends ButtonWidget {
        Supplier<ItemStack> icon;
        BooleanSupplier locked;
        BooleanSupplier selected;
        int accent = ACCENT;
        boolean danger;

        FlatButton(int x, int y, int w, int h, Text label, Runnable action) {
            super(x, y, w, h, label, b -> action.run(), DEFAULT_NARRATION_SUPPLIER);
        }

        FlatButton danger() {
            this.danger = true;
            return this;
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            float f = Math.max(fade, 0.02f);
            boolean hov = isHovered();
            boolean lock = locked != null && locked.getAsBoolean();
            boolean sel = selected != null && selected.getAsBoolean();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();

            int fill = sel ? 0xFF3A2F7A : lock ? LOCK_BG : hov ? CARD_HOVER : CARD;
            c.fill(x, y, x + w, y + h, a(fill, f));
            if (sel) c.fill(x, y, x + 2, y + h, a(ACCENT, f));
            if (lock) outline(c, x, y, w, h, a(LOCK, f));
            else if (hov) outline(c, x, y, w, h, a(danger ? LOCK : ACCENT, f));
            else if (!sel && icon == null) outline(c, x, y, w, h, a(accent == ACCENT ? 0xFF303650 : accent, f));

            ItemStack st = icon == null ? ItemStack.EMPTY : icon.get();
            if (!st.isEmpty()) c.drawItem(st, x + (w - 16) / 2, y + (h - 16) / 2);
            if (lock && icon != null) drawLock(c, x + w - 8, y + h - 9, a(GOLD, f));

            Text msg = getMessage();
            if (!msg.getString().isEmpty()) {
                int col = danger ? LOCK : (accent != ACCENT ? accent : TEXT);
                int tw = textRenderer.getWidth(msg);
                c.drawText(textRenderer, msg, x + (w - tw) / 2, y + (h - 8) / 2, a(col, f), false);
            }
        }
    }
}
