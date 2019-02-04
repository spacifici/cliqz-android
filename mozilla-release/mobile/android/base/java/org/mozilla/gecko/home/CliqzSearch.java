package org.mozilla.gecko.home;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.gecko.db.BrowserContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;

final class CliqzSearch {

    public static final class Keys {
        public static final String URL = "url";
        public static final String TITLE = "title";
        public static final String TYPE = "type";
    }

    public static final class Types {
        /* package */ static final int SEARCH_BACKEND = 1;
        /* package */ static final int LOCAL_HISTORY = 2;
    }

    private static final String LOGTAG = "CliqzSearch";
    private static final String JSON_KEY_RESULTS = "results";
    private static final Object[] PATH_SNIPPET_TITLE = new Object[] { "snippet", Keys.TITLE };

    private CliqzSearch() {}

    /* package */ static ArrayList<Bundle> parseBackendResults(String json) {
        try {
            final JSONObject obj = new JSONObject(json);
            final JSONArray results = obj.getJSONArray(JSON_KEY_RESULTS);
            return parseResults(results);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Can't parse the backend response");
            return new ArrayList<>();
        }
    }

    /* package */ static ArrayList<Bundle> convertHistoryResults(Cursor cursor) {
        if (cursor.getCount() == 0) {
            return new ArrayList<>();
        }
        final ArrayList<Bundle> results = new ArrayList<>(cursor.getCount());

        final int urlIndex = cursor.getColumnIndex(BrowserContract.URLColumns.URL);
        final int titleIndex = cursor.getColumnIndex(BrowserContract.URLColumns.TITLE);
        while (cursor.moveToNext()) {
            final String url = cursor.getString(urlIndex);
            final String title = cursor.getString(titleIndex);
            final Bundle bundle = new Bundle();
            bundle.putString(Keys.URL, url);
            bundle.putString(Keys.TITLE, title);
            bundle.putInt(Keys.TYPE, Types.LOCAL_HISTORY);
            results.add(bundle);
        }
        return results;
    }

    /* package */ static ArrayList<Bundle> combineSearchAndHistory(ArrayList<Bundle> searchData,
                                                                   ArrayList<Bundle> historyData) {
        if (searchData == null && historyData == null) {
            return new ArrayList<>();
        }

        if (historyData == null) {
            return searchData;
        }

        if (searchData == null) {
            return historyData;
        }

        if (searchData.size() + historyData.size() == 0) {
            return searchData; // is already empty
        }

        // This will effectively deduplicate history entries that have the same url
        final LinkedHashMap<String, Bundle> historyMap = convertToMap(historyData);
        // This will effectively deduplicate search entries that have the same url
        final LinkedHashMap<String, Bundle> searchMap = convertToMap(searchData);

        int historySize = historyMap.size();
        int searchSize = searchMap.size();
        final LinkedHashMap<String, Bundle> combined = new LinkedHashMap<>(searchSize + historySize);

        // Always add the first history result and remove the search result that has the same url if
        // it exists
        if (historySize > 0) {
            final Bundle first = historyData.get(0);
            final String url = first.getString(Keys.URL);
            combined.put(url, first);
            historyMap.remove(url);
            historySize--;
            searchMap.remove(url);
        }

        // Add first any result that also appear in history, if history is not empty
        if (historySize > 0) {
            final ArrayList<Bundle> copy = new ArrayList<>(searchMap.values());
            for (Bundle bundle : copy) {
                final String url = bundle.getString(Keys.URL);
                if (historyMap.containsKey(url)) {
                    combined.put(url, bundle);
                    searchMap.remove(url);
                    historyMap.remove(url);
                }
            }
        }

        // Add the remaining search results
        addAllValuesTo(searchMap, combined);

        // Add the remaining history results
        addAllValuesTo(historyMap, combined);

        return new ArrayList<>(combined.values());
    }

    private static void addAllValuesTo(LinkedHashMap<String, Bundle> from,
                                       LinkedHashMap<String, Bundle> to) {
        for (Bundle bundle: from.values()) {
            final String url = bundle.getString(Keys.URL);
            to.put(url, bundle);
        }
    }

    private static LinkedHashMap<String, Bundle> convertToMap(ArrayList<Bundle> data) {
        final LinkedHashMap<String, Bundle> out = new LinkedHashMap<>(data.size());
        for (Bundle bundle: data) {
            final String url = bundle.getString(Keys.URL);
            // The entries at the beginning of the list are more important
            if (!out.containsKey(url)) {
                out.put(url, bundle);
            }
        }
        return out;
    }

    private static ArrayList<Bundle> parseResults(JSONArray results) {
        final int len = results.length();
        final ArrayList<Bundle> out = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            try {
                final JSONObject obj = results.getJSONObject(i);
                final Bundle data = extractData(obj);
                out.add(data);
            } catch (JSONException e) {
                Log.w(LOGTAG, "Failed to parse result no. " + i);
            }
        }

        return out;
    }

    private static Bundle extractData(JSONObject jsonObject) {
        final Bundle result = new Bundle();
        final String url = jsonObject.optString(Keys.URL, "Error");
        result.putString(Keys.URL, url);
        final String snippetTitle = extractString(jsonObject, PATH_SNIPPET_TITLE, 0);
        final String title = snippetTitle != null ? snippetTitle : url;
        result.putString(Keys.TITLE, title);
        result.putInt(Keys.TYPE, Types.SEARCH_BACKEND);
        return result;
    }

    private static String extractString(JSONObject jsonObject, Object[] path, int index) {
        try {
            if (index == path.length - 1) {
                return jsonObject.optString((String) path[index]);
            } else {
                final JSONObject next = jsonObject.getJSONObject((String) path[index]);
                return extractString(next, path, index + 1);
            }
        } catch (JSONException ex) {
            return null;
        }
    }
}
