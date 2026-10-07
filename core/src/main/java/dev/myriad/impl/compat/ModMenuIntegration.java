package dev.myriad.impl.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.myriad.api.Myriad;
import dev.myriad.impl.ui.DesktopScreen;
import dev.myriad.impl.ui.WindowManager;

/** Mod Menu's "Configure" button opens the Myriad menu. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			WindowManager wm = (WindowManager) Myriad.ui();
			wm.refreshModules();
			return new DesktopScreen(wm);
		};
	}
}
