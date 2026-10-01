package dev.myriad.impl.ui.panels;

import dev.myriad.api.Myriad;
import dev.myriad.api.module.Category;
import dev.myriad.api.module.Module;
import dev.myriad.api.ui.WidgetPanel;
import dev.myriad.api.ui.widget.Collapsible;
import dev.myriad.api.ui.widget.Label;
import dev.myriad.api.ui.widget.TextField;
import dev.myriad.api.ui.widget.VBox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.myriad.api.util.FuzzyMatch;
import dev.myriad.api.util.MyriadId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every module, grouped by category, with a filter box. With a category argument it shows just that category,
 * like a classic click-GUI frame.
 */
public final class ModulesPanel extends WidgetPanel {
	private final Category only;
	private String filter = "";
	private final Map<Category, Boolean> expanded = new HashMap<>();
	/** Modules whose settings are unfolded. */
	private final Set<Module> unfolded = new HashSet<>();

	public ModulesPanel(Category only) {
		this.only = only;
	}

	@Override
	public String title() {
		return only == null ? "Modules" : AddonNames.title(only);
	}

	@Override
	public String icon() {
		return only == null ? "" : only.icon();
	}

	/** Name matches fuzzily; descriptions only on whole-word prefixes of 3+ letters, so short queries stay precise. */
	private boolean matches(Module m) {
		if (filter.isBlank()) return true;
		if (FuzzyMatch.matches(m.name(), filter)) return true;
		String f = filter.trim().toLowerCase(Locale.ROOT);
		if (f.length() < 3) return false;
		for (String word : m.description().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (word.startsWith(f)) return true;
		return false;
	}

	@Override
	public void save(JsonObject out) {
		JsonArray a = new JsonArray();
		for (Module m : unfolded) a.add(m.id().toString());
		out.add("unfolded", a);
	}

	@Override
	public void load(JsonObject in) {
		if (!in.has("unfolded")) return;
		for (var e : in.getAsJsonArray("unfolded")) Myriad.modules().get(MyriadId.parse(e.getAsString())).ifPresent(unfolded::add);
		rebuild();
	}

	@Override
	protected void build(VBox content) {
		content.add(new TextField(() -> filter).placeholder("  Filter modules…").onChange(s -> filter = s));
		if (Myriad.modules().size() == 0) {
			content.add(new Label("No modules installed. Drop Myriad addons (like myriad-essentials) into your mods folder.").dim());
			return;
		}
		for (Category category : Myriad.categories()) {
			List<Module> modules = Myriad.modules().inCategory(category);
			if (modules.isEmpty() || (only != null && !only.equals(category))) continue;
			VBox body = new VBox(2, 0);
			// Shared categories can mix addons; tag each module with its source when they do.
			boolean mixed = modules.stream().map(Myriad.modules()::ownerOf).distinct().count() > 1;
			for (Module m : modules) body.add(new ModuleEntry(m, unfolded, mixed)).visible(() -> matches(m));
			if (only != null) {
				content.add(body);
				content.add(new Label("No matching modules").dim()).visible(() -> !filter.isBlank() && modules.stream().noneMatch(this::matches));
				continue;
			}
			Collapsible section = new Collapsible(
				() -> category.icon() + "  " + category.name() + "  " + Myriad.modules().inCategory(category).stream().filter(Module::isEnabled).count() + "/" + modules.size(),
				body, () -> !filter.isBlank() || expanded.getOrDefault(category, true), v -> expanded.put(category, v));
			section.visible(() -> modules.stream().anyMatch(this::matches));
			content.add(section);
		}
	}
}
