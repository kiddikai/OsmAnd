package net.osmand.plus.track.helpers;

import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static net.osmand.IndexConstants.GPX_FILE_EXT;
import static net.osmand.IndexConstants.GPX_IMPORT_DIR;
import static net.osmand.IndexConstants.GPX_INDEX_DIR;
import static net.osmand.IndexConstants.GPX_RECORDED_INDEX_DIR;
import static net.osmand.router.network.NetworkRouteSelector.RouteKey;
import static net.osmand.shared.gpx.GpxParameter.*;
import static net.osmand.util.Algorithms.formatDuration;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.osmand.CallbackWithObject;
import net.osmand.IndexConstants;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.mapcontextmenu.controllers.SelectedGpxMenuController.SelectedGpxPoint;
import net.osmand.plus.mapcontextmenu.other.ShareMenu.NativeShareDialogBuilder;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu.ChartPointLayer;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.shared.SharedUtil;
import net.osmand.plus.track.GpxSelectionParams;
import net.osmand.plus.track.GpxSplitType;
import net.osmand.plus.track.SplitTrackAsyncTask;
import net.osmand.plus.track.data.GPXInfo;
import net.osmand.plus.track.fragments.TrackMenuFragment;
import net.osmand.plus.track.helpers.GpsFilterHelper.GpsFilter;
import net.osmand.plus.track.helpers.save.SaveGpxHelper;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.FileUtils;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.shared.gpx.GpxDataItem;
import net.osmand.shared.gpx.GpxDbHelper.GpxDataItemCallback;
import net.osmand.shared.gpx.GpxFile;
import net.osmand.shared.gpx.GpxTrackAnalysis;
import net.osmand.shared.gpx.GpxUtilities;
import net.osmand.shared.gpx.RouteActivityHelper;
import net.osmand.shared.gpx.TrackItem;
import net.osmand.shared.gpx.data.TrackFolder;
import net.osmand.shared.gpx.primitives.Metadata;
import net.osmand.shared.gpx.primitives.Track;
import net.osmand.shared.gpx.primitives.TrkSegment;
import net.osmand.shared.gpx.primitives.WptPt;
import net.osmand.shared.io.KFile;
import net.osmand.util.Algorithms;
import net.osmand.util.CollectionUtils;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class GpxUiHelper {

	private static final Log LOG = PlatformUtil.getLog(GpxUiHelper.class);


	public static String getColorValue(String clr, String value, boolean html) {
		if (!html) {
			return value;
		}
		return "<font color=\"" + clr + "\">" + value + "</font>";
	}

	public static String getColorValue(String clr, String value) {
		return getColorValue(clr, value, true);
	}

	public static String getDescription(OsmandApplication app, GpxTrackAnalysis analysis, boolean html) {
		StringBuilder description = new StringBuilder();
		String nl = html ? "<br/>" : "\n";
		String timeSpanClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_time_span_color));
		String distanceClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_distance_color));
		String speedClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_speed));
		String ascClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_altitude_asc));
		String descClr = Algorithms.colorToString(ContextCompat.getColor(app, R.color.gpx_altitude_desc));
		// OUTPUT:
		// 1. Total distance, Start time, End time
		description.append(app.getString(R.string.gpx_info_distance, getColorValue(distanceClr,
						OsmAndFormatter.getFormattedDistance(analysis.getTotalDistance(), app), html),
				getColorValue(distanceClr, analysis.getPoints() + "", html)));
		if (analysis.getTotalTracks() > 1) {
			description.append(nl).append(app.getString(R.string.gpx_info_subtracks, getColorValue(speedClr, analysis.getTotalTracks() + "", html)));
		}
		if (analysis.getWptPoints() > 0) {
			description.append(nl).append(app.getString(R.string.gpx_info_waypoints, getColorValue(speedClr, analysis.getWptPoints() + "", html)));
		}
		if (analysis.isTimeSpecified()) {
			description.append(nl).append(app.getString(R.string.gpx_info_start_time, analysis.getStartTime()));
			description.append(nl).append(app.getString(R.string.gpx_info_end_time, analysis.getEndTime()));
		}

		// 2. Time span
		if (analysis.getDurationInMs() > 0 && analysis.getDurationInMs() != analysis.getTimeMoving()) {
			String formatDuration = Algorithms.formatDuration(analysis.getDurationInSeconds(), app.accessibilityEnabled());
			description.append(nl).append(app.getString(R.string.gpx_timespan,
					getColorValue(timeSpanClr, formatDuration, html)));
		}

		// 3. Time moving, if any
		if (analysis.isTimeMoving()) {
			//Next few lines for Issue 3222 heuristic testing only
			//final String formatDuration0 = Algorithms.formatDuration((int) (analysis.timeMoving0 / 1000.0f + 0.5), app.accessibilityEnabled());
			//description.append(nl).append(app.getString(R.string.gpx_timemoving,
			//		getColorValue(timeSpanClr, formatDuration0, html)));
			//description.append(" (" + getColorValue(distanceClr, OsmAndFormatter.getFormattedDistance(analysis.totalDistanceMoving0, app), html) + ")");
			String formatDuration = Algorithms.formatDuration((int) (analysis.getTimeMoving() / 1000.0f + 0.5), app.accessibilityEnabled());
			description.append(nl).append(app.getString(R.string.gpx_timemoving,
					getColorValue(timeSpanClr, formatDuration, html)));
			description.append(" (" + getColorValue(distanceClr, OsmAndFormatter.getFormattedDistance(analysis.getTotalDistanceMoving(), app), html) + ")");
		}

		// 4. Elevation, eleUp, eleDown, if recorded
		if (analysis.isElevationSpecified()) {
			description.append(nl);
			description.append(app.getString(R.string.gpx_info_avg_altitude,
					getColorValue(speedClr, OsmAndFormatter.getFormattedAlt(analysis.getAvgElevation(), app), html)));
			description.append(nl);
			String min = getColorValue(descClr, OsmAndFormatter.getFormattedAlt(analysis.getMinElevation(), app), html);
			String max = getColorValue(ascClr, OsmAndFormatter.getFormattedAlt(analysis.getMaxElevation(), app), html);
			String asc = getColorValue(ascClr, OsmAndFormatter.getFormattedAlt(analysis.getDiffElevationUp(), app), html);
			String desc = getColorValue(descClr, OsmAndFormatter.getFormattedAlt(analysis.getDiffElevationDown(), app), html);
			description.append(app.getString(R.string.gpx_info_diff_altitude, min + " - " + max));
			description.append(nl);
			description.append(app.getString(R.string.gpx_info_asc_altitude, "\u2193 " + desc + "   \u2191 " + asc + ""));
		}


		if (analysis.isSpeedSpecified()) {
			String avg = getColorValue(speedClr, OsmAndFormatter.getFormattedSpeed(analysis.getAvgSpeed(), app), html);
			String max = getColorValue(ascClr, OsmAndFormatter.getFormattedSpeed(analysis.getMaxSpeed(), app), html);
			description.append(nl).append(app.getString(R.string.gpx_info_average_speed, avg));
			description.append(nl).append(app.getString(R.string.gpx_info_maximum_speed, max));
		}
		return description.toString();
	}

	@NonNull
	public static String getFolderName(@NonNull Context context, @NonNull File directory) {
		String name = directory.getName();
		if (GPX_INDEX_DIR.equals(name + File.separator)) {
			return context.getString(R.string.shared_string_tracks);
		}
		String dirPath = directory.getPath() + File.separator;
		if (dirPath.endsWith(GPX_IMPORT_DIR) || dirPath.endsWith(GPX_RECORDED_INDEX_DIR)) {
			return Algorithms.capitalizeFirstLetter(name);
		}
		return name;
	}

	@NonNull
	public static String getFolderPath(@NonNull File directory, @NonNull String initialName) {
		String name = directory.getName() + File.separator;
		File parent = directory.getParentFile();
		String parentName = parent != null ? parent.getName() + File.separator : "";
		if (!CollectionUtils.equalsToAny(GPX_INDEX_DIR, name, parentName)) {
			return parentName + initialName;
		}
		return initialName;
	}

	@NonNull
	public static String getFolderDescription(@NonNull OsmandApplication app, @NonNull TrackFolder folder) {
		long lastModified = folder.getLastModified();
		int tracksCount = folder.getFlattenedTrackItems().size();

		String empty = app.getString(R.string.shared_string_empty);
		String numberOfTracks = tracksCount > 0 ? app.getString(R.string.number_of_tracks, String.valueOf(tracksCount)) : empty;
		if (lastModified > 0) {
			String formattedDate = OsmAndFormatter.getFormattedDate(app, lastModified);
			return app.getString(R.string.ltr_or_rtl_combine_via_comma, formattedDate, numberOfTracks);
		}
		return numberOfTracks;
	}

	@NonNull
	public static String getGpxTitle(@Nullable String name) {
		return name != null ? Algorithms.getFileNameWithoutExtension(name) : "";
	}

	@NonNull
	public static String getGpxDirTitle(@Nullable String name) {
		if (Algorithms.isEmpty(name)) {
			return "";
		}
		return Algorithms.capitalizeFirstLetter(Algorithms.getFileNameWithoutExtension(name));
	}

	public static void updateGpxInfoView(@NonNull OsmandApplication app,
	                                     @NonNull View view,
	                                     @NonNull String itemTitle,
	                                     @Nullable Drawable iconDrawable,
	                                     @Nullable GPXInfo info) {
		if (info != null) {
			GpxDataItem item = getDataItem(app, info, dataItem -> updateGpxInfoView(app, view, itemTitle, iconDrawable, info, dataItem));
			if (item != null) {
				updateGpxInfoView(app, view, itemTitle, iconDrawable, info, item);
			}
		} else {
			updateGpxInfoView(view, itemTitle, null, null, app);
			if (iconDrawable != null) {
				ImageView icon = view.findViewById(R.id.icon);
				icon.setImageDrawable(iconDrawable);
				icon.setVisibility(View.VISIBLE);
			}
		}
	}

	private static void updateGpxInfoView(@NonNull OsmandApplication app,
	                                      @NonNull View view,
	                                      @NonNull String itemTitle,
	                                      @Nullable Drawable iconDrawable,
	                                      @NonNull GPXInfo info,
	                                      @NonNull GpxDataItem dataItem) {
		updateGpxInfoView(view, itemTitle, info, dataItem.getAnalysis(), app);
		if (iconDrawable != null) {
			ImageView icon = view.findViewById(R.id.icon);
			icon.setImageDrawable(iconDrawable);
			icon.setVisibility(View.VISIBLE);
		}
	}

	public static void updateGpxInfoView(@NonNull View v,
	                                     @NonNull String itemTitle,
	                                     @Nullable GPXInfo gpxInfo,
	                                     @Nullable GpxTrackAnalysis analysis,
	                                     @NonNull OsmandApplication app) {
		TextView viewName = v.findViewById(R.id.name);
		viewName.setText(itemTitle.replace("/", " • ").trim());
		viewName.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
		ImageView icon = v.findViewById(R.id.icon);
		icon.setVisibility(View.GONE);

		boolean hasGPXInfo = gpxInfo != null;
		boolean hasAnalysis = analysis != null;
		if (hasAnalysis) {
			ImageView distanceI = v.findViewById(R.id.distance_icon);
			distanceI.setVisibility(View.VISIBLE);
			distanceI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_distance_16));
			ImageView pointsI = v.findViewById(R.id.points_icon);
			pointsI.setVisibility(View.VISIBLE);
			pointsI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_waypoint_16));
			ImageView timeI = v.findViewById(R.id.time_icon);
			timeI.setVisibility(View.VISIBLE);
			timeI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_time_16));
			TextView time = v.findViewById(R.id.time);
			TextView distance = v.findViewById(R.id.distance);
			TextView pointsCount = v.findViewById(R.id.points_count);
			pointsCount.setText(String.valueOf(analysis.getWptPoints()));
			distance.setText(OsmAndFormatter.getFormattedDistance(analysis.getTotalDistance(), app));

			if (analysis.isTimeSpecified()) {
				time.setText(Algorithms.formatDuration(analysis.getDurationInSeconds(), app.accessibilityEnabled()));
			} else {
				time.setText("");
			}
		} else if (hasGPXInfo) {
			String date = "";
			String size = "";
			if (gpxInfo.getFileSize() >= 0) {
				size = AndroidUtils.formatSize(v.getContext(), gpxInfo.getFileSize());
			}
			DateFormat format = OsmAndFormatter.getDateFormat(app);
			long lastModified = gpxInfo.getLastModified();
			if (lastModified > 0) {
				date = (format.format(new Date(lastModified)));
			}
			TextView sizeText = v.findViewById(R.id.date_and_size_details);
			sizeText.setText(date + " • " + size);
		}
		AndroidUiHelper.updateVisibility(v.findViewById(R.id.check_item), false);
		AndroidUiHelper.updateVisibility(v.findViewById(R.id.description), false);
		AndroidUiHelper.updateVisibility(v.findViewById(R.id.read_section), hasAnalysis);
		AndroidUiHelper.updateVisibility(v.findViewById(R.id.unknown_section), !hasAnalysis && hasGPXInfo);
	}

	private static GpxDataItem getDataItem(@NonNull OsmandApplication app,
	                                       @NonNull GPXInfo info,
	                                       @Nullable GpxDataItemCallback callback) {
		KFile dir = app.getAppPathKt(IndexConstants.GPX_INDEX_DIR);
		String fileName = info.getFileName();
		KFile file = new KFile(dir, fileName);
		return app.getGpxDbHelper().getItem(file, callback);
	}

	@NonNull
	public static List<String> getSelectedTrackPaths(OsmandApplication app) {
		List<String> trackNames = new ArrayList<>();
		for (SelectedGpxFile file : app.getSelectedGpxHelper().getSelectedGPXFiles()) {
			trackNames.add(file.getGpxFile().getPath());
		}
		return trackNames;
	}

	public static List<GPXInfo> getSortedGPXFilesInfoByDate(File dir, boolean absolutePath) {
		List<GPXInfo> list = new ArrayList<>();
		readGpxDirectory(dir, list, "", absolutePath);
		Collections.sort(list, (object1, object2) -> {
			long lhs = object1.getLastModified();
			long rhs = object2.getLastModified();
			return Long.compare(rhs, lhs);
		});
		return list;
	}

	@Nullable
	public static GPXInfo getGpxInfoByFileName(@NonNull OsmandApplication app, @NonNull String fileName) {
		File dir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
		File file = new File(dir, fileName);
		if (file.exists() && isGpxFile(file)) {
			return new GPXInfo(fileName, file);
		}
		return null;
	}

	@NonNull
	public static List<GPXInfo> getGPXFiles(@NonNull File dir, boolean absolutePath) {
		return getGPXFiles(dir, absolutePath, true);
	}

	@NonNull
	public static List<GPXInfo> getGPXFiles(@NonNull File dir, boolean absolutePath, boolean includeSubFolders) {
		List<GPXInfo> gpxInfos = new ArrayList<>();
		readGpxDirectory(dir, gpxInfos, "", absolutePath, includeSubFolders);
		return gpxInfos;
	}

	@NonNull
	public static List<GPXInfo> getSortedGPXFilesInfo(File dir, List<String> selectedGpxList, boolean absolutePath) {
		List<GPXInfo> allGpxFiles = new ArrayList<>();
		readGpxDirectory(dir, allGpxFiles, "", absolutePath);
		if (selectedGpxList != null) {
			for (GPXInfo gpxInfo : allGpxFiles) {
				for (String selectedGpxName : selectedGpxList) {
					if (selectedGpxName.endsWith(gpxInfo.getFileName())) {
						gpxInfo.setSelected(true);
						break;
					}
				}
			}
		}
		Collections.sort(allGpxFiles, new Comparator<GPXInfo>() {
			@Override
			public int compare(GPXInfo i1, GPXInfo i2) {
				int res = i1.isSelected() == i2.isSelected() ? 0 : i1.isSelected() ? -1 : 1;
				if (res != 0) {
					return res;
				}

				String name1 = i1.getFileName();
				String name2 = i2.getFileName();
				int d1 = depth(name1);
				int d2 = depth(name2);
				if (d1 != d2) {
					return d1 - d2;
				}
				int lastSame = 0;
				for (int i = 0; i < name1.length() && i < name2.length(); i++) {
					if (name1.charAt(i) != name2.charAt(i)) {
						break;
					}
					if (name1.charAt(i) == '/') {
						lastSame = i + 1;
					}
				}

				boolean isDigitStarts1 = isLastSameStartsWithDigit(name1, lastSame);
				boolean isDigitStarts2 = isLastSameStartsWithDigit(name2, lastSame);
				res = isDigitStarts1 == isDigitStarts2 ? 0 : isDigitStarts1 ? -1 : 1;
				if (res != 0) {
					return res;
				}
				if (isDigitStarts1) {
					return -name1.compareToIgnoreCase(name2);
				}
				return name1.compareToIgnoreCase(name2);
			}

			private int depth(String name1) {
				int d = 0;
				for (int i = 0; i < name1.length(); i++) {
					if (name1.charAt(i) == '/') {
						d++;
					}
				}
				return d;
			}

			private boolean isLastSameStartsWithDigit(String name1, int lastSame) {
				if (name1.length() > lastSame) {
					return Character.isDigit(name1.charAt(lastSame));
				}

				return false;
			}
		});
		return allGpxFiles;
	}

	public static void readGpxDirectory(@Nullable File dir, @NonNull List<GPXInfo> list,
	                                    @NonNull String parent, boolean absolutePath) {
		readGpxDirectory(dir, list, parent, absolutePath, true);
	}

	public static void readGpxDirectory(@Nullable File dir, @NonNull List<GPXInfo> list,
	                                    @NonNull String parent, boolean absolutePath, boolean includeSubFolders) {
		if (dir != null && dir.canRead()) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (File file : files) {
					String name = file.getName();
					if (isGpxFile(file)) {
						String fileName = absolutePath ? file.getAbsolutePath() : parent + name;
						list.add(new GPXInfo(fileName, file));
					} else if (file.isDirectory() && includeSubFolders) {
						readGpxDirectory(file, list, parent + name + "/", absolutePath);
					}
				}
			}
		}
	}

	public static void loadGPXFileInDifferentThread(Activity activity, CallbackWithObject<GpxFile[]> callback,
	                                                File dir, GpxFile currentFile, String... filename) {
		ProgressDialog dlg = ProgressDialog.show(activity, activity.getString(R.string.loading_smth, ""),
				activity.getString(R.string.loading_data));
		new Thread(() -> {
			GpxFile[] result = new GpxFile[filename.length + (currentFile == null ? 0 : 1)];
			int k = 0;
			StringBuilder builder = new StringBuilder();
			if (currentFile != null) {
				result[k++] = currentFile;
			}
			for (String name : filename) {
				File file = new File(dir, name);
				GpxFile gpxFile = SharedUtil.loadGpxFile(file);
				Exception error = gpxFile.getError() != null ? SharedUtil.jException(gpxFile.getError()) : null;
				if (error != null && !Algorithms.isEmpty(error.getMessage())) {
					builder.append(error.getMessage()).append("\n");
				} else {
					gpxFile.addGeneralTrack();
				}
				result[k++] = gpxFile;
			}
			dlg.dismiss();
			String warn = builder.toString();
			activity.runOnUiThread(() -> {
				if (warn.length() > 0) {
					AndroidUtils.getApp(activity).showToastMessage(warn);
				} else {
					callback.processResult(result);
				}
			});
		}, "Loading gpx").start();
	}

	@NonNull
	public static GpxFile makeGpxFromRoute(RouteCalculationResult route, OsmandApplication app) {
		return makeGpxFromLocations(route.getRouteLocations(), app);
	}

	@NonNull
	public static GpxFile makeGpxFromLocations(List<Location> locations, OsmandApplication app) {
		GpxFile gpx = new GpxFile(Version.getFullVersion(app));
		if (locations != null) {
			Track track = new Track();
			TrkSegment seg = new TrkSegment();
			List<WptPt> pts = seg.getPoints();
			for (Location l : locations) {
				WptPt point = new WptPt();
				point.setLat(l.getLatitude());
				point.setLon(l.getLongitude());
				if (l.hasAltitude()) {
					gpx.setHasAltitude(true);
					point.setEle(l.getAltitude());
				}
				if (pts.size() == 0) {
					if (l.hasSpeed() && l.getSpeed() > 0) {
						point.setSpeed(l.getSpeed());
					}
					point.setTime(System.currentTimeMillis());
				} else {
					WptPt prevPoint = pts.get(pts.size() - 1);
					if (l.hasSpeed() && l.getSpeed() > 0) {
						point.setSpeed(l.getSpeed());
						double dist = MapUtils.getDistance(prevPoint.getLat(), prevPoint.getLon(), point.getLat(), point.getLon());
						point.setTime(prevPoint.getTime() + (long) (dist / point.getSpeed() * SECOND_IN_MILLIS));
					} else {
						point.setTime(prevPoint.getTime());
					}
				}
				pts.add(point);
			}
			GpxUtilities.INSTANCE.interpolateEmptyElevationWpts(pts);
			track.getSegments().add(seg);
			gpx.getTracks().add(track);
		}
		return gpx;
	}

	@Nullable
	public static GpxDisplayItem makeGpxDisplayItem(@NonNull OsmandApplication app, @NonNull GpxFile gpxFile,
	                                                @NonNull ChartPointLayer chartPointLayer, @Nullable GpxTrackAnalysis analysis) {
		TrackDisplayGroup group;
		if (!Algorithms.isEmpty(gpxFile.getTracks())) {
			group = app.getGpxDisplayHelper().buildTrackDisplayGroup(gpxFile);
			if (analysis == null) {
				SplitTrackAsyncTask.processGroupTrack(app, group, null, false);
				if (!Algorithms.isEmpty(group.getDisplayItems())) {
					GpxDisplayItem gpxItem = group.getDisplayItems().get(0);
					if (gpxItem != null) {
						gpxItem.chartPointLayer = chartPointLayer;
					}
					return gpxItem;
				}
			} else {
				List<TrkSegment> segments = gpxFile.getSegments(true);
				if (!Algorithms.isEmpty(segments)) {
					GpxDisplayItem gpxItem = SplitTrackAsyncTask.createGpxDisplayItem(app, group, segments.get(0), analysis);
					gpxItem.chartPointLayer = chartPointLayer;
					return gpxItem;
				}
			}
		}
		return null;
	}

	public static void saveAndShareGpx(@NonNull Context context, @NonNull Activity activity, @NonNull GpxFile gpxFile) {
		File file = getGpxTempFile(context, gpxFile);
		SaveGpxHelper.saveGpx(file, gpxFile, errorMessage -> {
			if (errorMessage == null) {
				shareGpx(context, activity, file);
			}
		});
	}

	@NonNull
	public static File getGpxTempFile(@NonNull Context context, @NonNull GpxFile gpxFile) {
		OsmandApplication app = (OsmandApplication) context.getApplicationContext();
		String fileName = Algorithms.getFileWithoutDirs(gpxFile.getPath());
		return new File(FileUtils.getTempDir(app), fileName);
	}

	public static void saveAndShareCurrentGpx(@NonNull OsmandApplication app, @NonNull Activity activity, @NonNull GpxFile gpxFile) {
		SaveGpxHelper.saveCurrentTrack(app, gpxFile, errorMessage -> {
			if (errorMessage == null) {
				shareGpx(app, activity, new File(gpxFile.getPath()));
			}
		});
	}

	public static void saveAndShareGpxWithAppearance(@NonNull OsmandApplication app, @NonNull Activity activity, @NonNull GpxFile gpxFile) {
		if (gpxFile.isShowCurrentTrack()) {
			saveAndShareCurrentGpx(app, activity, gpxFile);
		} else if (!Algorithms.isEmpty(gpxFile.getPath())) {
			KFile file = new KFile(gpxFile.getPath());
			GpxDataItem item = app.getGpxDbHelper().getItem(file, dataItem -> saveAndShareGpxWithAppearance(app, activity, gpxFile, dataItem));
			if (item != null) {
				saveAndShareGpxWithAppearance(app, activity, gpxFile, item);
			}
		}
	}

	public static void saveAndShareGpxWithAppearance(@NonNull OsmandApplication app, @NonNull Activity activity, @NonNull GpxFile gpxFile, @NonNull GpxDataItem item) {
		if (item.hasAppearanceData()) {
			addDbParametersToGpx(app, gpxFile, item);
			saveAndShareGpx(app, activity, gpxFile);
		} else {
			shareGpx(app, activity, new File(gpxFile.getPath()));
		}
	}

	public static void saveAndOpenGpx(@NonNull MapActivity mapActivity,
	                                  @NonNull File file,
	                                  @NonNull GpxFile gpxFile,
	                                  @NonNull WptPt selectedPoint,
	                                  @Nullable GpxTrackAnalysis analyses,
	                                  @Nullable RouteKey routeKey,
	                                  boolean adjustMapPosition) {
		SaveGpxHelper.saveGpx(file, gpxFile, errorMessage -> {
			if (errorMessage == null) {
				OsmandApplication app = mapActivity.getMyApplication();
				GpxSelectionParams params = GpxSelectionParams.getDefaultSelectionParams();
				SelectedGpxFile selectedGpxFile = app.getSelectedGpxHelper().selectGpxFile(gpxFile, params);
				GpxTrackAnalysis trackAnalysis = analyses != null ? analyses : selectedGpxFile.getTrackAnalysis(app);
				SelectedGpxPoint selectedGpxPoint = new SelectedGpxPoint(selectedGpxFile, selectedPoint);
				Bundle bundle = new Bundle();
				bundle.putBoolean(TrackMenuFragment.ADJUST_MAP_POSITION, adjustMapPosition);
				TrackMenuFragment.showInstance(mapActivity, selectedGpxFile, selectedGpxPoint,
						trackAnalysis, routeKey, bundle);
			} else {
				LOG.error(errorMessage);
			}
		});
	}

	private static void addDbParametersToGpx(@NonNull OsmandApplication app, @NonNull GpxFile gpxFile, @NonNull GpxDataItem item) {
		String activityId = item.getParameter(ACTIVITY_TYPE);
		if (!Algorithms.isEmpty(activityId)) {
			RouteActivityHelper helper = app.getRouteActivityHelper();
			Metadata metadata = gpxFile.getMetadata();
			metadata.setRouteActivity(helper.findRouteActivity(activityId));
		}
		addAppearanceToGpx(app, gpxFile, item);
	}

	private static void addAppearanceToGpx(@NonNull OsmandApplication app, @NonNull GpxFile gpxFile, @NonNull GpxDataItem item) {
		GpxAppearanceHelper helper = new GpxAppearanceHelper(app);
		gpxFile.setShowArrows(helper.requireParameter(item, SHOW_ARROWS));
		gpxFile.setShowStartFinish(helper.requireParameter(item, SHOW_START_FINISH));
		gpxFile.setSplitInterval(helper.requireParameter(item, SPLIT_INTERVAL));
		gpxFile.setSplitType(GpxSplitType.getSplitTypeByTypeId(helper.requireParameter(item, SPLIT_TYPE)).getTypeName());
		String visualizationType = helper.getParameter(item, TRACK_VISUALIZATION_TYPE);
		if (visualizationType != null) {
			gpxFile.set3DVisualizationType(visualizationType);
		}
		String wallColoringType = helper.getParameter(item, TRACK_3D_WALL_COLORING_TYPE);
		if (wallColoringType != null) {
			gpxFile.set3DWallColoringType(wallColoringType);
		}
		String linePositionType = helper.getParameter(item, TRACK_3D_LINE_POSITION_TYPE);
		if (linePositionType != null) {
			gpxFile.set3DLinePositionType(linePositionType);
		}
		gpxFile.setAdditionalExaggeration(((Double) helper.requireParameter(item, ADDITIONAL_EXAGGERATION)).floatValue());
		gpxFile.setElevationMeters(((Double) helper.requireParameter(item, ELEVATION_METERS)).floatValue());
		String colorPalette = helper.getParameter(item, COLOR_PALETTE);
		if (colorPalette != null) {
			gpxFile.setGradientColorPalette(colorPalette);
		}

		Integer color = helper.getParameter(item, COLOR);
		if (color != null) {
			gpxFile.setColor(color);
		}
		String width = helper.getParameter(item, WIDTH);
		if (width != null) {
			gpxFile.setWidth(width);
		}
		String coloringType = item.getParameter(COLORING_TYPE);
		if (coloringType != null) {
			gpxFile.setColoringType(coloringType);
		}
		String gradientPalette = item.getParameter(COLOR_PALETTE);
		if (gradientPalette != null) {
			gpxFile.setGradientColorPalette(gradientPalette);
		}
		GpsFilter.writeValidFilterValuesToExtensions(gpxFile.getExtensionsToWrite(), item);
	}

	public static void shareGpx(@NonNull Context context, @NonNull Activity activity, @NonNull File file) {
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		Uri fileUri = AndroidUtils.getUriForFile(context, file);
		boolean singleTop = !(activity instanceof MapActivity);

		new NativeShareDialogBuilder()
				.addFileWithSaveAction(file, app, activity, singleTop)
				.setChooserTitle(app.getString(R.string.shared_string_share))
				.setExtraStream(fileUri)
				.setNewTask(context instanceof OsmandApplication)
				.setType("application/gpx+xml")
				.build(app);
	}

	@NonNull
	public static String getGpxFileRelativePath(@NonNull OsmandApplication app, @NonNull String fullPath) {
		String rootGpxDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR).getAbsolutePath() + '/';
		return fullPath.replace(rootGpxDir, "");
	}

	public static boolean isGpxFile(@NonNull File file) {
		return file.getName().toLowerCase().endsWith(GPX_FILE_EXT);
	}

	public static void updateGpxInfoView(@NonNull View view, @NonNull TrackItem trackItem,
	                                     @NonNull OsmandApplication app, boolean isDashItem,
	                                     @Nullable GpxDataItemCallback callback) {
		TextView viewName = view.findViewById(R.id.name);
		if (isDashItem) {
			view.findViewById(R.id.divider_dash).setVisibility(View.VISIBLE);
			view.findViewById(R.id.divider_list).setVisibility(View.GONE);
		} else {
			view.findViewById(R.id.divider_list).setVisibility(View.VISIBLE);
			view.findViewById(R.id.divider_dash).setVisibility(View.GONE);
		}

		viewName.setText(trackItem.getName());

		ImageView icon = view.findViewById(R.id.icon);
		icon.setVisibility(View.VISIBLE);
		icon.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_polygom_dark));
		viewName.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

		if (getSelectedGpxFile(app, trackItem) != null) {
			icon.setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_polygom_dark, R.color.color_distance));
		}
		GpxTrackAnalysis analysis = getGpxTrackAnalysis(trackItem, app, callback);
		boolean sectionRead = analysis == null;
		if (sectionRead) {
			view.findViewById(R.id.read_section).setVisibility(View.GONE);
			view.findViewById(R.id.unknown_section).setVisibility(View.VISIBLE);
			String date = "";
			String size = "";

			KFile file = trackItem.getFile();
			long fileSize = file != null ? file.length() : 0;
			if (fileSize > 0) {
				size = AndroidUtils.formatSize(view.getContext(), fileSize + 512);
			}
			DateFormat format = OsmAndFormatter.getDateFormat(app);
			long lastModified = trackItem.getLastModified();
			if (lastModified > 0) {
				date = (format.format(new Date(lastModified)));
			}
			TextView sizeText = view.findViewById(R.id.date_and_size_details);
			sizeText.setText(date + " • " + size);

		} else {
			view.findViewById(R.id.read_section).setVisibility(View.VISIBLE);
			view.findViewById(R.id.unknown_section).setVisibility(View.GONE);
			ImageView distanceI = view.findViewById(R.id.distance_icon);
			distanceI.setVisibility(View.VISIBLE);
			distanceI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_distance_16));
			ImageView pointsI = view.findViewById(R.id.points_icon);
			pointsI.setVisibility(View.VISIBLE);
			pointsI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_waypoint_16));
			ImageView timeI = view.findViewById(R.id.time_icon);
			timeI.setVisibility(View.VISIBLE);
			timeI.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_time_16));
			TextView time = view.findViewById(R.id.time);
			TextView distance = view.findViewById(R.id.distance);
			TextView pointsCount = view.findViewById(R.id.points_count);
			pointsCount.setText(String.valueOf(analysis.getWptPoints()));
			distance.setText(OsmAndFormatter.getFormattedDistance(analysis.getTotalDistance(), app));

			if (analysis.isTimeSpecified()) {
				time.setText(formatDuration(analysis.getDurationInSeconds(), app.accessibilityEnabled()));
			} else {
				time.setText("");
			}
		}
		view.findViewById(R.id.description).setVisibility(View.GONE);
		view.findViewById(R.id.check_item).setVisibility(View.GONE);
	}

	private static SelectedGpxFile getSelectedGpxFile(@NonNull OsmandApplication app, @NonNull TrackItem trackItem) {
		GpxSelectionHelper selectedGpxHelper = app.getSelectedGpxHelper();
		return trackItem.isShowCurrentTrack() ? selectedGpxHelper.getSelectedCurrentRecordingTrack() :
				selectedGpxHelper.getSelectedFileByPath(trackItem.getPath());
	}

	@Nullable
	public static GpxTrackAnalysis getGpxTrackAnalysis(@NonNull TrackItem trackItem,
	                                                   @NonNull OsmandApplication app,
	                                                   @Nullable GpxDataItemCallback callback) {
		SelectedGpxFile selectedGpxFile = getSelectedGpxFile(app, trackItem);
		GpxTrackAnalysis analysis = null;
		if (selectedGpxFile != null && selectedGpxFile.isLoaded()) {
			analysis = selectedGpxFile.getTrackAnalysis(app);
		} else if (trackItem.isShowCurrentTrack()) {
			analysis = app.getSavingTrackHelper().getCurrentTrack().getTrackAnalysis(app);
		} else if (trackItem.getFile() != null) {
			GpxDataItem dataItem = app.getGpxDbHelper().getItem(trackItem.getFile(), callback);
			if (dataItem != null) {
				analysis = dataItem.getAnalysis();
			}
		}
		return analysis;
	}
}