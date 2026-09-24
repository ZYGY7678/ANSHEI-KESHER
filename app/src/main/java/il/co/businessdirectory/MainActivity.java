package il.co.businessdirectory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private ListView list;
    private EditText search;
    private TextView status;
    private TextView title;
    private Button cityButton;
    private Button categoryButton;
    private Button allButton;

    private String selectedCity = "";
    private String selectedCategory = "";
    private BusinessAdapter adapter;

    private final ArrayList<Business> allBusinesses = new ArrayList<Business>();
    private final ArrayList<Business> visibleBusinesses = new ArrayList<Business>();
    private final ArrayList<Locality> localities = new ArrayList<Locality>();
    private final Set<String> seen = new HashSet<String>();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        list = (ListView) findViewById(R.id.list);
        search = (EditText) findViewById(R.id.search);
        status = (TextView) findViewById(R.id.status);
        title = (TextView) findViewById(R.id.title);
        cityButton = (Button) findViewById(R.id.cityButton);
        categoryButton = (Button) findViewById(R.id.categoryButton);
        allButton = (Button) findViewById(R.id.allButton);

        adapter = new BusinessAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showBusinessMenu(visibleBusinesses.get(position));
            }
        });

        cityButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickCity(); }
        });
        categoryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickCategory(); }
        });
        allButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedCity = "";
                selectedCategory = "";
                refresh();
            }
        });

        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        status.setText("טוען את המאגר הלאומי…");
        new LoadTask().execute();
    }

    private class LoadTask extends AsyncTask<Void, String, Boolean> {
        protected Boolean doInBackground(Void... args) {
            boolean ok = false;
            try {
                loadLocalities();
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(getAssets().open("businesses.csv"), "UTF-8"), 32768);
                String line;
                boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; continue; }
                    String[] p = csv(line);
                    if (p.length >= 5) {
                        String city = p.length > 0 ? p[0] : "";
                        String category = p.length > 1 ? p[1] : "";
                        String name = p.length > 2 ? p[2] : "";
                        String address = p.length > 3 ? p[3] : "";
                        String phone = p.length > 4 ? p[4] : "";
                        String status = p.length > 5 ? p[5] : "";
                        String license = p.length > 6 ? p[6] : "";
                        String lat = p.length > 7 ? p[7] : "";
                        String lon = p.length > 8 ? p[8] : "";
                        String website = p.length > 9 ? p[9] : "";
                        String hours = p.length > 10 ? p[10] : "";
                        String source = p.length > 11 ? p[11] : "";
                        if (addBusiness(city, category, name, address, phone, status,
                                        license, lat, lon, website, hours, source)) {
                            ok = true;
                        }
                    }
                }
                br.close();
            } catch (Exception ignored) {
                ok = false;
            }
            return ok;
        }

        protected void onPostExecute(Boolean ok) {
            if (!ok) {
                status.setText("לא נמצא מאגר עסקים זמין");
            } else {
                refresh();
            }
        }
    }

    private boolean addBusiness(String city, String category, String name, String address,
                                String phone, String bizStatus, String licenseDate,
                                String lat, String lon, String website, String hours, String source) {
        city = clean(city); category = clean(category); name = clean(name); address = clean(address);
        phone = clean(phone);
        if (name.length() == 0 || phone.length() == 0) return false;
        String[] phones = phone.split("[;,/]+");
        boolean added = false;
        for (int i = 0; i < phones.length; i++) {
            String ph = clean(phones[i]);
            if (digits(ph).length() < 6) continue;
            String key = city.toLowerCase() + "|" + name.toLowerCase() + "|" +
                    digits(ph) + "|" + address.toLowerCase();
            if (seen.contains(key)) continue;
            seen.add(key);
            allBusinesses.add(new Business(city, category, name, address, ph, bizStatus,
                    licenseDate, lat, lon, website, hours, source));
            added = true;
        }
        return added;
    }

    private void loadLocalities() {
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(getAssets().open("localities.csv"), "UTF-8"), 16384);
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                String[] p = csv(line);
                if (p.length >= 1 && clean(p[0]).length() > 0) {
                    localities.add(new Locality(
                        clean(p[0]),
                        p.length > 1 ? clean(p[1]) : "",
                        p.length > 2 ? clean(p[2]) : "",
                        p.length > 3 ? clean(p[3]) : "",
                        p.length > 4 ? clean(p[4]) : "",
                        p.length > 5 ? clean(p[5]) : "",
                        p.length > 6 ? clean(p[6]) : "",
                        p.length > 7 ? clean(p[7]) : "",
                        p.length > 8 ? clean(p[8]) : ""
                    ));
                }
            }
            br.close();
        } catch (Exception ignored) {
            localities.clear();
        }
    }

    private void refresh() {
        visibleBusinesses.clear();
        String q = clean(search.getText().toString()).toLowerCase();
        for (Business b : allBusinesses) {
            if (selectedCity.length() > 0 && !b.city.equals(selectedCity)) continue;
            if (selectedCategory.length() > 0 && !b.category.equals(selectedCategory)) continue;
            if (q.length() > 0 && !b.searchText().contains(q)) continue;
            visibleBusinesses.add(b);
        }
        adapter.notifyDataSetChanged();

        cityButton.setText(selectedCity.length() == 0 ? "כל היישובים" : selectedCity);
        categoryButton.setText(selectedCategory.length() == 0 ? "כל התחומים" : selectedCategory);
        title.setText(selectedCity.length() == 0
                ? "עסקים וטלפונים בישראל"
                : "עסקים ב" + selectedCity);
        status.setText(formatNumber(visibleBusinesses.size()) + " תוצאות  •  " +
                formatNumber(allBusinesses.size()) + " רשומות במאגר  •  " +
                formatNumber(localities.size()) + " יישובים");
        list.requestFocus();
    }

    private void pickCity() {
        final ArrayList<String> allCities = new ArrayList<String>();
        allCities.add("כל היישובים");
        Map<String,Integer> counts = new HashMap<String,Integer>();

        for (int i = 0; i < localities.size(); i++) {
            String n = localities.get(i).name;
            if (n.length() > 0) counts.put(n, 0);
        }
        for (Business b : allBusinesses) {
            Integer n = counts.get(b.city);
            counts.put(b.city, n == null ? 1 : n + 1);
        }

        ArrayList<String> names = new ArrayList<String>(counts.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (String n : names) allCities.add(n + "  (" + formatNumber(counts.get(n)) + ")");

        final ArrayList<String> shown = new ArrayList<String>(allCities);
        final EditText filter = new EditText(this);
        filter.setHint("חיפוש עיר / יישוב / מועצה");
        filter.setSingleLine(true);
        filter.setTextColor(android.graphics.Color.WHITE);
        filter.setHintTextColor(android.graphics.Color.GRAY);
        filter.setPadding(18, 10, 18, 10);
        filter.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        final ListView cityList = new ListView(this);
        cityList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        cityList.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_single_choice, shown));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(10, 4, 10, 4);
        box.addView(filter, new LinearLayout.LayoutParams(-1, 54));
        box.addView(cityList, new LinearLayout.LayoutParams(-1, 0, 1));

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("בחירת עיר / יישוב / מועצה")
                .setView(box)
                .setNegativeButton("ביטול", null)
                .create();

        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = clean(s.toString()).toLowerCase();
                shown.clear();
                for (String x : allCities) {
                    if (q.length() == 0 || x.toLowerCase().contains(q)) shown.add(x);
                }
                cityList.setAdapter(new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_list_item_single_choice, shown));
                cityList.requestFocus();
            }
            public void afterTextChanged(android.text.Editable e) {}
        });

        cityList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                String z = shown.get(p);
                selectedCity = p == 0 && z.equals("כל היישובים") ? "" :
                        z.substring(0, z.lastIndexOf("  ("));
                d.dismiss();
                refresh();
            }
        });
        d.show();
        cityList.requestFocus();
    }

    private void pickCategory() {
        final ArrayList<String> allCategories = new ArrayList<String>();
        allCategories.add("כל התחומים");
        Set<String> set = new HashSet<String>();
        for (Business b : allBusinesses) if (b.category.length() > 0) set.add(b.category);
        ArrayList<String> names = new ArrayList<String>(set);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        allCategories.addAll(names);

        final ArrayList<String> shown = new ArrayList<String>(allCategories);
        final EditText filter = new EditText(this);
        filter.setHint("חיפוש תחום / קטגוריה");
        filter.setSingleLine(true);
        filter.setTextColor(android.graphics.Color.WHITE);
        filter.setHintTextColor(android.graphics.Color.GRAY);
        filter.setPadding(18, 10, 18, 10);
        filter.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        final ListView categoryList = new ListView(this);
        categoryList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        categoryList.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_single_choice, shown));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(10, 4, 10, 4);
        box.addView(filter, new LinearLayout.LayoutParams(-1, 54));
        box.addView(categoryList, new LinearLayout.LayoutParams(-1, 0, 1));

        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("בחירת תחום")
                .setView(box)
                .setNegativeButton("ביטול", null)
                .create();

        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = clean(s.toString()).toLowerCase();
                shown.clear();
                for (String x : allCategories) {
                    if (q.length() == 0 || x.toLowerCase().contains(q)) shown.add(x);
                }
                categoryList.setAdapter(new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_list_item_single_choice, shown));
                categoryList.requestFocus();
            }
            public void afterTextChanged(android.text.Editable e) {}
        });

        categoryList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                selectedCategory = (p == 0 && shown.get(p).equals("כל התחומים"))
                        ? "" : shown.get(p);
                d.dismiss();
                refresh();
            }
        });
        d.show();
        categoryList.requestFocus();
    }

    private void showBusinessMenu(final Business b) {
        final String[] items = new String[] {
            "חייג " + b.phone,
            "פתח מיקום במפה",
            "העתק מספר",
            "פתח אתר",
            "פרטי העסק והמקור"
        };
        new AlertDialog.Builder(this)
            .setTitle(b.name)
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) dial(b.phone);
                    else if (which == 1) openMap(b);
                    else if (which == 2) copy(b.phone);
                    else if (which == 3) openWebsite(b.website);
                    else showDetails(b);
                }
            })
            .setNegativeButton("סגור", null)
            .show();
    }

    private void showDetails(Business b) {
        StringBuilder s = new StringBuilder();
        if (b.category.length() > 0) s.append("תחום: ").append(b.category).append("\n");
        if (b.city.length() > 0) s.append("יישוב: ").append(b.city).append("\n");
        if (b.address.length() > 0) s.append("כתובת: ").append(b.address).append("\n");
        s.append("טלפון: ").append(b.phone).append("\n");
        if (b.status.length() > 0) s.append("סטטוס: ").append(b.status).append("\n");
        if (b.licenseDate.length() > 0) s.append("תאריך רישוי: ").append(b.licenseDate).append("\n");
        if (b.hours.length() > 0) s.append("שעות: ").append(b.hours).append("\n");
        if (b.website.length() > 0) s.append("אתר: ").append(b.website).append("\n");
        if (b.lat.length() > 0 || b.lon.length() > 0)
            s.append("מיקום: ").append(b.lat).append(", ").append(b.lon).append("\n");
        if (b.source.length() > 0) s.append("מקור: ").append(b.source);
        new AlertDialog.Builder(this)
            .setTitle("פרטי העסק")
            .setMessage(s.toString())
            .setPositiveButton("חייג", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { dial(b.phone); }
            })
            .setNegativeButton("סגור", null)
            .show();
    }

    private void dial(String phone) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL);
            i.setData(Uri.parse("tel:" + phone.replaceAll("[^0-9+]", "")));
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private void openMap(Business b) {
        try {
            Uri u;
            if (isNumber(b.lat) && isNumber(b.lon)) {
                u = Uri.parse("geo:" + b.lat + "," + b.lon + "?q=" +
                        Uri.encode(b.name + " " + b.address));
            } else {
                u = Uri.parse("geo:0,0?q=" + Uri.encode(b.name + " " + b.address + " " + b.city));
            }
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) {}
    }

    private void openWebsite(String url) {
        try {
            if (url == null || url.length() == 0) return;
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    private void copy(String value) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("phone", value));
            Toast.makeText(this, "המספר הועתק", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private boolean isNumber(String s) {
        try { Double.parseDouble(s); return true; } catch (Exception e) { return false; }
    }

    private String[] csv(String s) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                if (q && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    cur.append('"'); i++;
                } else q = !q;
            } else if (c == ',' && !q) {
                out.add(cur.toString()); cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[out.size()]);
    }

    private String clean(String s) { return s == null ? "" : s.trim(); }
    private String digits(String s) { return s == null ? "" : s.replaceAll("[^0-9]", ""); }

    private String formatNumber(int n) {
        return String.format(java.util.Locale.US, "%,d", n);
    }

    @Override
    public void onBackPressed() {
        if (search.getText().length() > 0) {
            search.setText("");
            return;
        }
        if (selectedCategory.length() > 0) {
            selectedCategory = "";
            refresh();
            return;
        }
        if (selectedCity.length() > 0) {
            selectedCity = "";
            refresh();
            return;
        }
        super.onBackPressed();
    }

    private class BusinessAdapter extends BaseAdapter {
        public int getCount() { return visibleBusinesses.size(); }
        public Object getItem(int p) { return visibleBusinesses.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int p, View v, ViewGroup parent) {
            View row = v;
            if (row == null) row = getLayoutInflater().inflate(R.layout.row_business, parent, false);
            Business b = visibleBusinesses.get(p);
            ((TextView) row.findViewById(R.id.name)).setText(b.name);
            ((TextView) row.findViewById(R.id.meta)).setText(
                    (b.city.length() == 0 ? "" : b.city) +
                    (b.category.length() == 0 ? "" : "  •  " + b.category));
            ((TextView) row.findViewById(R.id.address)).setText(b.address);
            ((TextView) row.findViewById(R.id.phone)).setText("☎  " + b.phone);
            row.setFocusable(true);
            return row;
        }
    }

    private static class Business {
        String city, category, name, address, phone, status, licenseDate, lat, lon, website, hours, source;
        Business(String city, String category, String name, String address, String phone,
                 String status, String licenseDate, String lat, String lon,
                 String website, String hours, String source) {
            this.city=city; this.category=category; this.name=name; this.address=address; this.phone=phone;
            this.status=status; this.licenseDate=licenseDate; this.lat=lat; this.lon=lon;
            this.website=website; this.hours=hours; this.source=source;
        }
        String searchText() {
            return (city+" "+category+" "+name+" "+address+" "+phone+" "+status).toLowerCase();
        }
    }

    private static class Locality {
        String name, code, district, municipalStatus, cluster, population, grid, elevation, nameEn;
        Locality(String name, String code, String district, String municipalStatus, String cluster,
                 String population, String grid, String elevation, String nameEn) {
            this.name=name; this.code=code; this.district=district; this.municipalStatus=municipalStatus;
            this.cluster=cluster; this.population=population; this.grid=grid;
            this.elevation=elevation; this.nameEn=nameEn;
        }
    }
}
