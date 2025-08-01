package net.osmand.plus.settings.backend.backup.items;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.OsmandPlugin;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.custom.CustomOsmandPlugin;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.ApplicationMode.ApplicationModeBuilder;
import net.osmand.plus.settings.backend.ApplicationModeBean;
import net.osmand.plus.settings.backend.backup.*;
import net.osmand.plus.settings.backend.preferences.OsmandPreference;
import net.osmand.util.Algorithms;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProfileSettingsItem extends OsmandSettingsItem {

	private ApplicationMode appMode;
	private ApplicationModeBuilder builder;
	private ApplicationModeBean modeBean;

	private JSONObject additionalPrefsJson;
	private Set<String> appModeBeanPrefsIds;

	public ProfileSettingsItem(@NonNull OsmandApplication app, @NonNull ApplicationMode appMode) {
		super(app.getSettings());
		this.appMode = appMode;
		modeBean = appMode.toModeBean();
	}

	public ProfileSettingsItem(@NonNull OsmandApplication app,
			@Nullable ProfileSettingsItem baseItem, @NonNull ApplicationModeBean modeBean) {
		super(app.getSettings(), baseItem);
		this.modeBean = modeBean;
		builder = ApplicationMode.fromModeBean(app, modeBean);
		appMode = builder.getApplicationMode();
	}

	public ProfileSettingsItem(@NonNull OsmandApplication app,
			@NonNull JSONObject json) throws JSONException {
		super(SettingsItemType.PROFILE, app.getSettings(), json);
	}

	@Override
	protected void init() {
		super.init();
		appModeBeanPrefsIds = ApplicationModeBean.getAppModeBeanPrefsIds(app);
	}

	@NonNull
	@Override
	public SettingsItemType getType() {
		return SettingsItemType.PROFILE;
	}

	@Override
	public long getLocalModifiedTime() {
		return getSettings().getLastModePreferencesEditTime(appMode);
	}

	@Override
	public void setLocalModifiedTime(long lastModifiedTime) {
		getSettings().setLastModePreferencesEditTime(appMode, lastModifiedTime);
	}

	@Override
	public long getEstimatedSize() {
		return (long) getSettings().getSavedModePrefsCount(appMode) * APPROXIMATE_PREFERENCE_SIZE_BYTES;
	}

	public ApplicationMode getAppMode() {
		return appMode;
	}

	public ApplicationModeBean getModeBean() {
		return modeBean;
	}

	@NonNull
	@Override
	public String getName() {
		return appMode.getStringKey();
	}

	@NonNull
	@Override
	public String getPublicName(@NonNull Context ctx) {
		if (appMode.isCustomProfile()) {
			return modeBean.userProfileName;
		} else if (appMode.getNameKeyResource() != -1) {
			return ctx.getString(appMode.getNameKeyResource());
		} else {
			return getName();
		}
	}

	@NonNull
	@Override
	public String getDefaultFileName() {
		return "profile_" + getName() + getDefaultFileExtension();
	}

	@Override
	void readFromJson(@NonNull JSONObject json) throws JSONException {
		super.readFromJson(json);
		String appModeJson = json.getString("appMode");
		modeBean = ApplicationMode.fromJson(app, appModeJson);
		builder = ApplicationMode.fromModeBean(app, modeBean);
		ApplicationMode appMode = builder.getApplicationMode();
		if (!appMode.isCustomProfile()) {
			appMode = ApplicationMode.valueOfStringKey(appMode.getStringKey(), appMode);
		}
		this.appMode = appMode;
	}

	@Override
	void readItemsFromJson(@NonNull JSONObject json) throws IllegalArgumentException {
		additionalPrefsJson = json.optJSONObject("prefs");
	}

	@Override
	public boolean exists() {
		return builder != null && ApplicationMode.valueOfStringKey(getName(), null) != null;
	}

	private void renameProfile() {
		List<ApplicationMode> values = ApplicationMode.allPossibleValues();
		if (Algorithms.isEmpty(modeBean.userProfileName)) {
			ApplicationMode appMode = ApplicationMode.valueOfStringKey(modeBean.stringKey, null);
			if (appMode != null) {
				modeBean.userProfileName = appMode.toHumanString();
			}
		}
		int number = 0;
		while (true) {
			number++;
			String key = modeBean.stringKey + "_" + number;
			String name = modeBean.userProfileName + '_' + number;
			if (ApplicationMode.valueOfStringKey(key, null) == null && isNameUnique(values, name)) {
				modeBean.userProfileName = name;
				modeBean.stringKey = key;
				break;
			}
		}
	}

	private boolean isNameUnique(List<ApplicationMode> values, String name) {
		for (ApplicationMode mode : values) {
			if (mode.getUserProfileName().equals(name)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void apply() {
		if (!appMode.isCustomProfile() && !shouldReplace) {
			ApplicationMode parent = ApplicationMode.valueOfStringKey(modeBean.stringKey, null);
			renameProfile();

			ApplicationModeBuilder builder = ApplicationMode
					.createCustomMode(parent, modeBean.stringKey, app)
					.setIconResName(modeBean.iconName)
					.setUserProfileName(modeBean.userProfileName)
					.setRoutingProfile(modeBean.routingProfile)
					.setRouteService(modeBean.routeService)
					.setIconColor(modeBean.iconColor)
					.setLocationIcon(modeBean.locIcon)
					.setNavigationIcon(modeBean.navIcon);
			getSettings().copyPreferencesFromProfile(parent, builder.getApplicationMode(), true);
			appMode = ApplicationMode.saveProfile(builder, app);
		} else if (!shouldReplace && exists()) {
			renameProfile();
			builder = ApplicationMode.fromModeBean(app, modeBean);
			appMode = ApplicationMode.saveProfile(builder, app);
		} else {
			builder = ApplicationMode.fromModeBean(app, modeBean);
			appMode = ApplicationMode.saveProfile(builder, app);
		}
	}

	@Override
	public void delete() {
		super.delete();
		ApplicationMode.deleteCustomModes(Collections.singletonList(appMode), app);
	}

	public void applyAdditionalParams(@Nullable SettingsItemReader<? extends SettingsItem> reader) {
		if (additionalPrefsJson != null) {
			updatePluginResPrefs();
			if (reader instanceof OsmandSettingsItemReader) {
				((OsmandSettingsItemReader<?>) reader).readPreferencesFromJson(additionalPrefsJson);
			}
		}
	}

	private void updatePluginResPrefs() {
		String pluginId = getPluginId();
		if (Algorithms.isEmpty(pluginId)) {
			return;
		}
		OsmandPlugin plugin = PluginsHelper.getPlugin(pluginId);
		if (plugin instanceof CustomOsmandPlugin) {
			CustomOsmandPlugin customPlugin = (CustomOsmandPlugin) plugin;
			String resDirPath = IndexConstants.PLUGINS_DIR + pluginId + "/" + customPlugin.getResourceDirName();

			for (Iterator<String> it = additionalPrefsJson.keys(); it.hasNext(); ) {
				try {
					String prefId = it.next();
					Object value = additionalPrefsJson.get(prefId);
					if (value instanceof JSONObject) {
						JSONObject jsonObject = (JSONObject) value;
						for (Iterator<String> iterator = jsonObject.keys(); iterator.hasNext(); ) {
							String key = iterator.next();
							Object val = jsonObject.get(key);
							if (val instanceof String) {
								val = checkPluginResPath((String) val, resDirPath);
							}
							jsonObject.put(key, val);
						}
					} else if (value instanceof String) {
						value = checkPluginResPath((String) value, resDirPath);
						additionalPrefsJson.put(prefId, value);
					}
				} catch (JSONException e) {
					SettingsHelper.LOG.error(e);
				}
			}
		}
	}

	private String checkPluginResPath(String path, String resDirPath) {
		if (path.startsWith("@")) {
			return resDirPath + "/" + path.substring(1);
		}
		return path;
	}

	@Override
	void writeToJson(@NonNull JSONObject json) throws JSONException {
		super.writeToJson(json);
		json.put("appMode", new JSONObject(appMode.toJson()));
	}

	@Nullable
	@Override
	public SettingsItemReader<? extends SettingsItem> getReader() {
		return new ProfileSettingsItemReader(this);
	}

	@Nullable
	@Override
	public SettingsItemWriter<? extends SettingsItem> getWriter() {
		return new OsmandSettingsItemWriter<>(this, getSettings()) {
			@Override
			protected void writePreferenceToJson(@NonNull OsmandPreference<?> preference,
					@NonNull JSONObject json) throws JSONException {
				if (!appModeBeanPrefsIds.contains(preference.getId())) {
					preference.writeToJson(json, appMode);
				}
			}
		};
	}
}
