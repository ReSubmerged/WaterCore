package org.susnetwork.mangocore;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import org.json.JSONObject;
import org.json.JSONTokener;

import org.susnetwork.mangocore.tools.CrashDetector;
import org.susnetwork.mangocore.tools.GlobalMetadata;
import org.susnetwork.mangocore.tools.Utilities;

public class SelectorActivity extends AppCompatActivity {
    private static final String TAG = "FusionCore";
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 1001;
    private static final String[] UNITY_ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};

    private String pendingLaunchPackage;
    private String pendingMetadataPackage;
    private final ActivityResultLauncher<String[]> modPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::addSelectedMod);
    private final ActivityResultLauncher<String[]> modIconPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::saveSelectedModIcon);
    private String selectedModFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(resolveAppTheme(getSharedPreferences("appearance", MODE_PRIVATE).getString("theme", "Black")));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selector);

        View root = findViewById(R.id.selector_root);
        int basePadding = Math.round(getResources().getDisplayMetrics().density * 16f);
        Utilities.applyWindowInsets(root, basePadding);

        var handler = new Handler(getMainLooper());
        handler.postDelayed(() -> {
            showGameStatus();
            if (!hasExternalStorageManagerAccess()) requestExternalStorageManagerAccess();
            else CrashDetector.init(this);
        }, 100);
    }

    private int resolveAppTheme(String theme) {
        switch (theme) {
            case "Light": return R.style.MangoTheme_Light;
            case "Red": return R.style.MangoTheme_Red;
            case "Black (AMOLED)": return R.style.MangoTheme_Amoled;
            default: return R.style.MangoTheme_Black;
        }
    }

    private void showGameStatus() {
        boolean installed;
        try {
            getPackageManager().getApplicationInfo("com.innersloth.spacemafia", 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        View launchButton = findViewById(R.id.launch_button);
        launchButton.setEnabled(installed);
        launchButton.setAlpha(installed ? 1f : 0.45f);
        launchButton.setOnClickListener(v -> maybeLaunchBootstrap("com.innersloth.spacemafia"));
        View homeContent = findViewById(R.id.home_content);
        View homeHeader = findViewById(R.id.home_header);
        View modsContent = findViewById(R.id.mods_content);
        View shopContent = findViewById(R.id.shop_content);
        View settingsContent = findViewById(R.id.settings_content);
        View personalisationContent = findViewById(R.id.personalisation_content);
        View addModButton = findViewById(R.id.add_mod_button);
        View modActions = findViewById(R.id.mod_actions);
        View bottomTabs = findViewById(R.id.bottom_tabs);
        View homeTab = findViewById(R.id.tab_home);
        View modsTab = findViewById(R.id.tab_mods);
        View settingsTab = findViewById(R.id.tab_settings);
        Runnable selectHome = () -> animateTabSelection(homeTab, modsTab, settingsTab, homeTab);
        Runnable selectMods = () -> animateTabSelection(homeTab, modsTab, settingsTab, modsTab);
        Runnable selectSettings = () -> animateTabSelection(homeTab, modsTab, settingsTab, settingsTab);
        ImageView gameIcon = findViewById(R.id.game_icon);
        try {
            gameIcon.setImageDrawable(getPackageManager().getApplicationIcon("com.innersloth.spacemafia"));
        } catch (PackageManager.NameNotFoundException e) {
            gameIcon.setImageResource(R.mipmap.app_icon);
        }
        View actionShop = findViewById(R.id.action_shop);
        View actionUpload = findViewById(R.id.action_upload);
        refreshModsList();
        SwitchCompat libUnitySwitch = findViewById(R.id.home_switch_libunity);
        SwitchCompat il2cpp2monoSwitch = findViewById(R.id.home_switch_il2cpp2mono);
        libUnitySwitch.setChecked(FusionSettings.getUseUnstrippedLibUnityForGame(this, "com.innersloth.spacemafia"));
        il2cpp2monoSwitch.setChecked(FusionSettings.getUseIl2Cpp2Mono(this, "com.innersloth.spacemafia"));
        libUnitySwitch.setOnCheckedChangeListener((button, checked) -> FusionSettings.setUseUnstrippedLibUnityForGame(this, "com.innersloth.spacemafia", checked));
        il2cpp2monoSwitch.setOnCheckedChangeListener((button, checked) -> FusionSettings.setUseIl2Cpp2Mono(this, "com.innersloth.spacemafia", checked));
        setupCustomShopAddress();
        View personalisationEntry = findViewById(R.id.personalisation_entry);
        personalisationEntry.setOnClickListener(v -> {
            settingsContent.setVisibility(View.GONE);
            personalisationContent.setVisibility(View.VISIBLE);
        });
        findViewById(R.id.personalisation_back).setOnClickListener(v -> {
            personalisationContent.setVisibility(View.GONE);
            settingsContent.setVisibility(View.VISIBLE);
        });
        Spinner themeSpinner = findViewById(R.id.app_theme_spinner);
        ArrayAdapter<CharSequence> themeAdapter = ArrayAdapter.createFromResource(this, R.array.app_themes, android.R.layout.simple_spinner_item);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(themeAdapter);
        String savedTheme = getSharedPreferences("appearance", MODE_PRIVATE).getString("theme", "Black");
        String[] themes = getResources().getStringArray(R.array.app_themes);
        themeSpinner.setSelection(java.util.Arrays.asList(themes).indexOf(savedTheme));
        themeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = themes[position];
                String current = getSharedPreferences("appearance", MODE_PRIVATE).getString("theme", "Black");
                if (!selected.equals(current)) {
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putString("theme", selected).apply();
                    recreate();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        addModButton.setRotation(0f);
        modActions.setVisibility(View.GONE);
        homeTab.setOnClickListener(v -> {
            selectHome.run();
            homeContent.setVisibility(View.VISIBLE);
            homeHeader.setVisibility(View.VISIBLE);
            modsContent.setVisibility(View.GONE);
            shopContent.setVisibility(View.GONE);
            settingsContent.setVisibility(View.GONE);
            bottomTabs.setVisibility(View.VISIBLE);
            addModButton.setVisibility(View.GONE);
            modActions.setVisibility(View.GONE);
            addModButton.setRotation(0f);
        });
        modsTab.setOnClickListener(v -> {
            selectMods.run();
            homeContent.setVisibility(View.GONE);
            homeHeader.setVisibility(View.GONE);
            modsContent.setVisibility(View.VISIBLE);
            shopContent.setVisibility(View.GONE);
            settingsContent.setVisibility(View.GONE);
            bottomTabs.setVisibility(View.VISIBLE);
            addModButton.setVisibility(View.VISIBLE);
            modActions.setVisibility(View.GONE);
            addModButton.setRotation(0f);
        });
        actionShop.setOnClickListener(v -> {
            homeContent.setVisibility(View.GONE);
            homeHeader.setVisibility(View.GONE);
            modsContent.setVisibility(View.GONE);
            shopContent.setVisibility(View.VISIBLE);
            bottomTabs.setVisibility(View.GONE);
            addModButton.setVisibility(View.GONE);
            modActions.setVisibility(View.GONE);
            addModButton.animate().rotation(0f).setDuration(180).start();
        });
        findViewById(R.id.shop_back).setOnClickListener(v -> {
            shopContent.setVisibility(View.GONE);
            modsContent.setVisibility(View.VISIBLE);
            bottomTabs.setVisibility(View.VISIBLE);
            addModButton.setVisibility(View.VISIBLE);
        });
        findViewById(R.id.mod_detail_back).setOnClickListener(v -> {
            findViewById(R.id.mod_detail_content).setVisibility(View.GONE);
            modsContent.setVisibility(View.VISIBLE);
            bottomTabs.setVisibility(View.VISIBLE);
            refreshModsList();
        });
        findViewById(R.id.mod_upload_icon).setOnClickListener(v -> modIconPicker.launch(new String[]{"image/png", "image/jpeg", "image/*"}));
        findViewById(R.id.mod_save_name).setOnClickListener(v -> saveModDisplayName());
        findViewById(R.id.mod_detail_back).setOnClickListener(v -> {
            findViewById(R.id.mod_detail_content).setVisibility(View.GONE);
            modsContent.setVisibility(View.VISIBLE);
            bottomTabs.setVisibility(View.VISIBLE);
            refreshModsList();
        });
        findViewById(R.id.mod_upload_icon).setOnClickListener(v -> modIconPicker.launch(new String[]{"image/png", "image/jpeg", "image/*"}));
        findViewById(R.id.mod_save_name).setOnClickListener(v -> saveModDisplayName());
        actionUpload.setOnClickListener(v -> {
            modActions.setVisibility(View.GONE);
            addModButton.animate().rotation(0f).setDuration(180).start();
            modPicker.launch(new String[]{"application/octet-stream", "application/x-dosexec", "*/*"});
        });
        settingsTab.setOnClickListener(v -> {
            selectSettings.run();
            homeContent.setVisibility(View.GONE);
            homeHeader.setVisibility(View.GONE);
            modsContent.setVisibility(View.GONE);
            findViewById(R.id.mod_detail_content).setVisibility(View.GONE);
            shopContent.setVisibility(View.GONE);
            settingsContent.setVisibility(View.VISIBLE);
            bottomTabs.setVisibility(View.VISIBLE);
            addModButton.setVisibility(View.GONE);
            modActions.setVisibility(View.GONE);
        });
        addModButton.setOnClickListener(v -> {
            boolean open = modActions.getVisibility() != View.VISIBLE;
            modActions.setVisibility(open ? View.VISIBLE : View.GONE);
            addModButton.animate().rotation(open ? 45f : 0f).setDuration(180).start();
        });
    }

    private void animateTabSelection(View home, View mods, View settings, View selected) {
        for (View tab : new View[]{home, mods, settings}) {
            boolean active = tab == selected;
            ImageView icon = (ImageView) ((ViewGroup) tab).getChildAt(0);
            TextView label = (TextView) ((ViewGroup) tab).getChildAt(1);
            int color = active
                    ? com.google.android.material.color.MaterialColors.getColor(tab, android.R.attr.colorAccent)
                    : ContextCompat.getColor(this, android.R.color.darker_gray);
            icon.setColorFilter(color);
            label.setTextColor(color);
            tab.animate().scaleX(active ? 1.06f : 1f).scaleY(active ? 1.06f : 1f).setDuration(160).start();
            if (active) {
                icon.setTranslationY(0f);
                icon.animate().translationY(-4f).setDuration(140).setInterpolator(new OvershootInterpolator()).withEndAction(
                        () -> icon.animate().translationY(0f).setDuration(140).start()).start();
            }
        }
    }

    private void addSelectedMod(Uri uri) {
        if (uri == null) return;
        String name = getFileName(uri);
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".dll")) {
            Toast.makeText(this, R.string.mods_invalid_file, Toast.LENGTH_LONG).show();
            return;
        }
        File pluginsDir = pluginsDirectory();
        if (!pluginsDir.exists() && !pluginsDir.mkdirs()) {
            Toast.makeText(this, R.string.mods_copy_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(new File(pluginsDir, new File(name).getName()))) {
            if (in == null) throw new java.io.IOException("Could not open selected DLL");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            refreshModsList();
            Toast.makeText(this, R.string.mods_added, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy mod DLL", e);
            Toast.makeText(this, R.string.mods_copy_failed, Toast.LENGTH_LONG).show();
        }
    }

    private String getFileName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to read selected file name", e);
        }
        return uri.getLastPathSegment();
    }

    private File pluginsDirectory() { return new File(getExternalFilesDir(null), "MangoData/com.innersloth.spacemafia/BepInEx/plugins"); }
    private File modInfoDirectory() {
        File dir = new File(getExternalFilesDir(null), "MangoData/com.innersloth.spacemafia/ModInfo");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    private File modIconFile(File mod) { return new File(modInfoDirectory(), mod.getName() + ".png"); }
    private File modNameFile(File mod) { return new File(modInfoDirectory(), mod.getName() + ".txt"); }

    private String modDisplayName(File mod) {
        File metadata = modNameFile(mod);
        if (metadata.isFile()) try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(metadata))) {
            String value = reader.readLine(); if (value != null && !value.trim().isEmpty()) return value.trim();
        } catch (Exception e) { Log.w(TAG, "Could not read mod name", e); }
        return mod.getName();
    }

    private void refreshModsList() {
        ListView list = findViewById(R.id.mods_list);
        if (list == null) return;
        File[] files = pluginsDirectory().listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".dll"));
        List<File> mods = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        Collections.sort(mods, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        findViewById(R.id.mods_empty).setVisibility(mods.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(mods.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new ArrayAdapter<File>(this, R.layout.item_mod, R.id.mod_row_name, mods) {
            @NonNull @Override public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = convertView != null ? convertView : LayoutInflater.from(SelectorActivity.this).inflate(R.layout.item_mod, parent, false);
                File mod = getItem(position);
                if (mod != null) {
                    ((TextView) row.findViewById(R.id.mod_row_name)).setText(modDisplayName(mod));
                    ImageView icon = row.findViewById(R.id.mod_row_icon);
                    File image = modIconFile(mod);
                    if (image.isFile()) { icon.setImageURI(Uri.fromFile(image)); icon.setScaleType(ImageView.ScaleType.CENTER_CROP); }
                    else { icon.setImageResource(R.drawable.ic_mod_placeholder); icon.setScaleType(ImageView.ScaleType.CENTER); }
                    row.setOnClickListener(v -> openModDetail(mod));
                }
                return row;
            }
        });
    }

    private void openModDetail(File mod) {
        selectedModFile = mod.getName();
        findViewById(R.id.mods_content).setVisibility(View.GONE);
        findViewById(R.id.mod_detail_content).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_tabs).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.mod_detail_title)).setText(mod.getName());
        ((EditText) findViewById(R.id.mod_display_name)).setText(modDisplayName(mod));
        ImageView icon = findViewById(R.id.mod_detail_icon);
        File image = modIconFile(mod);
        if (image.isFile()) { icon.setImageURI(Uri.fromFile(image)); icon.setScaleType(ImageView.ScaleType.CENTER_CROP); }
        else { icon.setImageResource(R.drawable.ic_mod_placeholder); icon.setScaleType(ImageView.ScaleType.CENTER); }
    }

    private void saveModDisplayName() {
        if (selectedModFile == null) return;
        String name = ((EditText) findViewById(R.id.mod_display_name)).getText().toString().trim();
        if (name.isEmpty()) name = selectedModFile;
        try (FileOutputStream out = new FileOutputStream(modNameFile(new File(selectedModFile)))) {
            out.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this, R.string.mod_name_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Log.e(TAG, "Could not save mod name", e); }
    }

    private void saveSelectedModIcon(Uri uri) {
        if (uri == null || selectedModFile == null) return;
        File image = modIconFile(new File(selectedModFile));
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(image)) {
            if (in == null) throw new java.io.IOException("Cannot read selected icon");
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            ImageView detailIcon = findViewById(R.id.mod_detail_icon); detailIcon.setImageURI(Uri.fromFile(image)); detailIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Toast.makeText(this, R.string.mod_icon_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Log.e(TAG, "Could not save mod icon", e); Toast.makeText(this, R.string.mod_icon_failed, Toast.LENGTH_LONG).show(); }
    }

    private void setupCustomShopAddress() {
        File configFile = new File(getFilesDir(), "config.json");
        EditText addressField = findViewById(R.id.custom_shop_address);
        addressField.setText(readShopAddress(configFile));
        addressField.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) saveShopAddress(configFile, addressField.getText().toString());
        });
        addressField.setOnEditorActionListener((view, actionId, event) -> {
            saveShopAddress(configFile, addressField.getText().toString());
            view.clearFocus();
            return false;
        });
    }

    private String readShopAddress(File configFile) {
        if (!configFile.isFile()) return "";
        try (FileReader reader = new FileReader(configFile)) {
            StringBuilder json = new StringBuilder(); char[] buffer = new char[4096]; int count;
            while ((count = reader.read(buffer)) != -1) json.append(buffer, 0, count);
            JSONObject object = new JSONObject(new JSONTokener(json.toString()));
            return object.optString("ShopURL", "");
        } catch (Exception e) {
            Log.e(TAG, "Could not read config.json", e);
            return "";
        }
    }

    private void saveShopAddress(File configFile, String value) {
        JSONObject object = new JSONObject();
        if (configFile.isFile()) {
            try (FileReader reader = new FileReader(configFile)) {
                StringBuilder json = new StringBuilder(); char[] buffer = new char[4096]; int count;
                while ((count = reader.read(buffer)) != -1) json.append(buffer, 0, count);
                object = new JSONObject(new JSONTokener(json.toString()));
            } catch (Exception e) { Log.e(TAG, "Could not parse config.json; preserving ShopURL in a fresh config", e); }
        }
        try (FileOutputStream out = new FileOutputStream(configFile, false)) {
            object.put("ShopURL", value);
            out.write((object.toString(2) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Could not save ShopURL", e);
        }
    }

    private void populateList() {
        ListView listView = findViewById(R.id.selector_list);
        TextView emptyView = findViewById(R.id.selector_empty);
        listView.setEmptyView(emptyView);

        List<AppEntry> installedTargets = resolveInstalledTargets();
        Drawable defaultIcon = getPackageManager().getDefaultActivityIcon();
        ArrayAdapter<AppEntry> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_selector_target,
                installedTargets
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                RowHolder holder;
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_selector_target, parent, false);
                    holder = new RowHolder(
                            convertView.findViewById(R.id.row_icon),
                            convertView.findViewById(R.id.row_name),
                            convertView.findViewById(R.id.row_package),
                            convertView.findViewById(R.id.row_version)
                    );
                    convertView.setTag(holder);
                } else {
                    holder = (RowHolder) convertView.getTag();
                }

                AppEntry entry = getItem(position);
                if (entry != null) {
                    holder.icon.setImageDrawable(entry.icon != null ? entry.icon : defaultIcon);
                    holder.name.setText(entry.label);
                    holder.packageName.setText(entry.packageName);
                    String version = Utilities.formatVersionText(entry.versionName, entry.versionCode);
                    holder.version.setText(version);

                    ImageButton settingsButton = convertView.findViewById(R.id.selector_action_settings);
                    settingsButton.setOnClickListener(v -> {
                        var intent = new Intent(getContext(), GameSettingsActivity.class);
                        intent.putExtra(GameSettingsActivity.EXTRA_PACKAGE_NAME, entry.packageName);
                        startActivity(intent);
                    });

                    ImageButton folderButton = convertView.findViewById(R.id.selector_action_folder);
                    folderButton.setOnClickListener(v -> {
                        File folder = Utilities.getExternalFusionCoreDirectory(entry.packageName, null);

                        if (!folder.exists() && !folder.mkdirs()) {
                            String message = getString(R.string.selector_folder_create_failed, folder.getAbsolutePath());
                            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                            Log.e(TAG, message);
                            return;
                        }

                        try {
                            String relativePath = "FusionCore/" + entry.packageName;
                            Uri directoryUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(relativePath));

                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(directoryUri, "vnd.android.document/directory");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Unable to open folder", e);
                            Toast.makeText(getContext(), getString(R.string.selector_no_file_manager), Toast.LENGTH_LONG).show();
                        }
                    });

                    convertView.setOnClickListener((v) -> maybeLaunchBootstrap(entry.packageName));
                }

                return convertView;
            }
        };
        listView.setAdapter(adapter);
        findViewById(R.id.selector_loading).setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingLaunchPackage != null && hasExternalStorageManagerAccess()) {
            String packageName = pendingLaunchPackage;
            pendingLaunchPackage = null;
            launchBootstrap(packageName);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_MANAGE_EXTERNAL_STORAGE || pendingLaunchPackage == null) {
            return;
        }

        if (hasExternalStorageManagerAccess()) {
            CrashDetector.init(this);
            if (pendingLaunchPackage != null) {
                String packageName = pendingLaunchPackage;
                pendingLaunchPackage = null;
                launchBootstrap(packageName);
            }
            return;
        }

        Toast.makeText(this, getString(R.string.selector_storage_permission_required), Toast.LENGTH_LONG).show();
    }

    private List<AppEntry> resolveInstalledTargets() {
        PackageManager pm = getPackageManager();
        List<AppEntry> result = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();

        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL);

        for (ResolveInfo resolveInfo : activities) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (packageName == null || !seenPackages.add(packageName)) {
                continue;
            }
            if (packageName.equals(getPackageName())) {
                continue;
            }

            ApplicationInfo info;
            try {
                info = pm.getApplicationInfo(packageName, 0);
            } 
            catch (PackageManager.NameNotFoundException e) {
                continue;
            }

            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }

            if (!hasIl2Cpp(info)) {
                continue;
            }

            String label = packageName;
            Drawable icon = pm.getDefaultActivityIcon();
            String versionName = "Unknown";
            long versionCode = 0L;
            try {
                label = pm.getApplicationLabel(info).toString();
                icon = pm.getApplicationIcon(info);

                PackageInfo packageInfo;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageInfo(packageName, 0);
                }
                if (packageInfo.versionName != null && !packageInfo.versionName.isEmpty()) {
                    versionName = packageInfo.versionName;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    versionCode = packageInfo.getLongVersionCode();
                } else {
                    //noinspection deprecation
                    versionCode = packageInfo.versionCode;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resolve metadata for package: " + packageName, e);
            }

            Log.i(TAG, "Found installed target: " + packageName + " (" + label + ")");
            result.add(new AppEntry(packageName, label, icon, versionName, versionCode,
                    GlobalMetadata.isEncrypted(info)));
        }

        return result;
    }

    private static boolean hasIl2Cpp(ApplicationInfo info) {
        List<String> apkPaths = new ArrayList<>();
        if (info.sourceDir != null) {
            apkPaths.add(info.sourceDir);
        }
        if (info.splitSourceDirs != null) {
            Collections.addAll(apkPaths, info.splitSourceDirs);
        }
        for (String apk : apkPaths) {
            if (apkContainsIl2Cpp(apk)) {
                return true;
            }
        }

        String nativeDir = info.nativeLibraryDir;
        if (nativeDir != null && !nativeDir.isEmpty()) {
            File dir = new File(nativeDir);
            if (new File(dir, "libil2cpp.so").exists()) {
                return true;
            }
            File[] abiDirs = dir.listFiles();
            if (abiDirs != null) {
                for (File abiDir : abiDirs) {
                    if (abiDir.isDirectory() && new File(abiDir, "libil2cpp.so").exists()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean apkContainsIl2Cpp(String apkPath) {
        try {
            try (ZipFile zip = new ZipFile(apkPath)) {
                for (String abi : UNITY_ABIS) {
                    if (zip.getEntry("lib/" + abi + "/libil2cpp.so") != null) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Unreadable APK; fall through to the nativeLibraryDir check.
        }
        return false;
    }

    private void launchBootstrap(String packageName) {
        if (requestMetadataIfNeeded(packageName)) {
            return;
        }

        Intent intent = new Intent(this, BootstrapActivity.class);
        intent.putExtra(BootstrapActivity.EXTRA_TARGET_PACKAGE, packageName);
        intent.putExtra(BootstrapActivity.EXTRA_USE_ORIGINAL_LIBUNITY,
                !FusionSettings.getUseUnstrippedLibUnityForGame(this, packageName));
        intent.putExtra(BootstrapActivity.EXTRA_USE_IL2CPP2MONO,
                FusionSettings.getUseIl2Cpp2Mono(this, packageName));

        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        //noinspection deprecation
        overridePendingTransition(0, 0);
        finish();
        //noinspection deprecation
        overridePendingTransition(0, 0);
    }

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGrantedMap -> {
                for (Map.Entry<String, Boolean> entry : isGrantedMap.entrySet()) {
                    String permission = entry.getKey();
                    boolean isGranted = entry.getValue();

                    if (isGranted) {
                        Log.i(TAG, "Got permission: " +permission);
                    } else {
                        Log.e(TAG, "Permission denied: " +permission);
                    }
                }

                String packageName = pendingLaunchPackage;
                pendingLaunchPackage = null;
                if (packageName != null) {
                    launchBootstrap(packageName);
                }
            });

    private void maybeLaunchBootstrap(String packageName) {
        if (!hasExternalStorageManagerAccess()) {
            pendingLaunchPackage = packageName;
            requestExternalStorageManagerAccess();
            return;
        }

        if (requestMetadataIfNeeded(packageName)) {
            return;
        }

        try {
            var packageInfo = getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            var perms = packageInfo.requestedPermissions;
            if (perms != null) {
                ArrayList<String> newPerms = new ArrayList<>();
                for (var p : perms) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        newPerms.add(p);
                    }
                }

                if (!newPerms.isEmpty()) {
                    pendingLaunchPackage = packageName;
                    requestPermissionsLauncher.launch(newPerms.toArray(new String[0]));
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failure getting package info for " + packageName, e);
        }

        launchBootstrap(packageName);
    }

    private final ActivityResultLauncher<String[]> pickMetadataLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                String packageName = pendingMetadataPackage;
                pendingMetadataPackage = null;
                if (uri == null || packageName == null) {
                    return;
                }

                new Thread(() -> {
                    boolean installed;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        installed = in != null && GlobalMetadata.installOverride(in, packageName);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to install metadata override for " + packageName, e);
                        installed = false;
                    }

                    boolean success = installed;
                    runOnUiThread(() -> {
                        if (success) {
                            maybeLaunchBootstrap(packageName);
                        } else {
                            Toast.makeText(this, getString(R.string.selector_metadata_invalid), Toast.LENGTH_LONG).show();
                        }
                    });
                }).start();
            });

    private boolean requestMetadataIfNeeded(String packageName) {
        if (FusionSettings.getUseIl2Cpp2Mono(this, packageName)) {
            return false;
        }
        if (GlobalMetadata.hasValidOverride(packageName)) {
            return false;
        }
        try {
            if (!GlobalMetadata.isEncrypted(getPackageManager().getApplicationInfo(packageName, 0))) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.selector_metadata_title)
                .setMessage(getString(R.string.selector_metadata_message))
                .setPositiveButton(R.string.selector_metadata_select, (dialog, which) -> {
                    pendingMetadataPackage = packageName;
                    pickMetadataLauncher.launch(new String[]{"*/*"});
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return true;
    }

    private boolean hasExternalStorageManagerAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        return Environment.isExternalStorageManager();
    }

    private void requestExternalStorageManagerAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }

        Toast.makeText(this, getString(R.string.selector_storage_permission_prompt), Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open app-specific all-files access screen, opening generic page", e);
            Intent fallbackIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            try {
                startActivityForResult(fallbackIntent, REQUEST_MANAGE_EXTERNAL_STORAGE);
            } catch (Exception inner) {
                Log.e(TAG, "Failed to open all-files access settings", inner);
                Toast.makeText(this, getString(R.string.selector_storage_permission_open_failed), Toast.LENGTH_LONG).show();
            }
        }
    }

    private record AppEntry(String packageName, String label, Drawable icon, String versionName,
                            long versionCode, boolean encryptedMetadata) {

        @NonNull
        @Override
        public String toString() {
            if (label.equals(packageName)) {
                return packageName;
            }
            return label + " (" + packageName + ")";
        }
    }

    private record RowHolder(ImageView icon, TextView name, TextView packageName,
                             TextView version) {
    }
}
